package com.expedia.mcp.reviewkit

import com.expedia.mcp.reviewkit.config.ConfigLoader
import com.expedia.mcp.reviewkit.config.ReviewerConfig
import com.expedia.mcp.reviewkit.engine.MarkdownReportComposer
import com.expedia.mcp.reviewkit.engine.RulesEngine
import com.expedia.mcp.reviewkit.llm.LlmClient
import com.expedia.mcp.reviewkit.mcp.*
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    val env = System.getenv()
    val eventName = env["GITHUB_EVENT_NAME"] ?: ""
    val eventPath = env["GITHUB_EVENT_PATH"] ?: error("GITHUB_EVENT_PATH not set")
    val event = ObjectMapper().readTree(File(eventPath))

    val input = { name: String -> env["INPUT_" + name.uppercase().replace('-', '_').replace(' ', '_')] }

    val configPath = input("config") ?: ".github/pr-reviewer.yml"
    val failOn = (input("fail-on") ?: "error").lowercase()
    val enableLlm = (input("enable-llm") ?: "false").toBooleanStrictOrNull() == true && !env["OPENAI_API_KEY"].isNullOrBlank()

    val ghMcpUrl = input("mcp-github-url") ?: env["MCP_GITHUB_URL"] ?: error("MCP GitHub URL not provided")
    val confMcpUrl = input("mcp-confluence-url") ?: env["MCP_CONFLUENCE_URL"]
    val jiraMcpUrl = input("mcp-jira-url") ?: env["MCP_JIRA_URL"]
    val mcpAuthToken = env["MCP_AUTH_TOKEN"]

    val config: ReviewerConfig = ConfigLoader.load(configPath)
    val repoFull = env["GITHUB_REPOSITORY"] ?: error("GITHUB_REPOSITORY not set")
    val owner = repoFull.substringBefore('/')
    val repo = repoFull.substringAfter('/')

    when (eventName) {
        "issue_comment" -> {
            val isPr = event.path("issue").has("pull_request")
            if (!isPr) return@runBlocking
            val prNumber = event.path("issue").path("number").asInt()
            val commentBody = event.path("comment").path("body").asText("")
            val triggerCmd = config.jira.commands.firstOrNull { commentBody.trim().startsWith(it) }
            if (triggerCmd == null || jiraMcpUrl.isNullOrBlank() || !config.jira.enabled) return@runBlocking
            McpClient(ghMcpUrl, mcpAuthToken).use { ghConn ->
                ghConn.connect()
                val gh = GitHubMcp(ghConn)
                val pr = gh.getPullRequest(owner, repo, prNumber)
                val summary = pr.title?.take(240) ?: "PR #$prNumber"
                val desc = buildString {
                    appendLine("Created from PR: https://github.com/$owner/$repo/pull/$prNumber")
                    appendLine("")
                    appendLine(pr.body ?: "")
                }
                McpClient(jiraMcpUrl, mcpAuthToken).use { jiraConn ->
                    jiraConn.connect()
                    val jira = JiraMcp(jiraConn)
                    val labels = config.jira.defaultLabels.map { it.replace("{repo}", repo) }
                    val issue = jira.createIssue(config.jira.projectKey, config.jira.issueType, summary, desc, labels)
                    val msg = "Created Jira: ${issue.key}${issue.url?.let { " ($it)" } ?: ""}"
                    gh.createIssueComment(owner, repo, prNumber, msg)
                }
            }
        }
        "pull_request", "pull_request_target" -> {
            val prNumber = event.path("number").asInt().let { if (it != 0) it else event.path("pull_request").path("number").asInt() }
            if (prNumber == 0) error("Could not determine PR number from event")
            McpClient(ghMcpUrl, mcpAuthToken).use { ghConn ->
                ghConn.connect()
                val gh = GitHubMcp(ghConn)
                val pr = gh.getPullRequest(owner, repo, prNumber)
                val files = gh.listPullRequestFiles(owner, repo, prNumber)

                val engine = RulesEngine(config)
                val results = engine.run(pr, files)

                val confluenceSnippets = if (!confMcpUrl.isNullOrBlank() && config.confluence.enabled) {
                    McpClient(confMcpUrl, mcpAuthToken).use { confConn ->
                        confConn.connect()
                        val conf = ConfluenceMcp(confConn)
                        val snippets = mutableListOf<String>()
                        val limit = config.confluence.maxSnippets
                        for (q in config.confluence.queries.take(5)) {
                            conf.search(q, limit = 3).forEach { s -> snippets.add("- ${s.title}: ${s.url}") }
                            if (snippets.size >= limit) break
                        }
                        snippets.take(limit)
                    }
                } else emptyList()

                val llmText = if (enableLlm && config.llm.enabled) {
                    val apiKey = env["OPENAI_API_KEY"]!!
                    val llm = LlmClient(apiKey)
                    val systemPrompt = "You are a senior Kotlin reviewer at Expedia. Provide concise, actionable feedback."
                    val changedSummary = files.joinToString("\n") { f -> "- ${f.filename} (+${f.additions ?: 0}/-${f.deletions ?: 0})" }
                    val userPrompt = buildString {
                        appendLine("PR Title: ${pr.title}")
                        appendLine("Changed files:\n$changedSummary")
                        if (confluenceSnippets.isNotEmpty()) { appendLine("Relevant docs:"); confluenceSnippets.forEach { appendLine(it) } }
                        appendLine("\nAssess architecture/coupling, risks, tests sufficiency, and missing docs. Keep it under 200 words.")
                    }
                    try { llm.chatAnalyze(systemPrompt, userPrompt) } catch (_: Exception) { null }
                } else null

                val body = MarkdownReportComposer.build(results, llmText)
                val existing = gh.listIssueComments(owner, repo, prNumber)
                val marker = MarkdownReportComposer.marker()
                val prev = existing.firstOrNull { it.body?.startsWith(marker) == true }
                if (prev == null) gh.createIssueComment(owner, repo, prNumber, body)
                else gh.updateIssueComment(owner, repo, prev.id, body)

                val shouldFail = when (failOn) {
                    "none" -> false
                    "warn" -> results.errors.isNotEmpty()
                    else -> results.errors.isNotEmpty()
                }
                if (shouldFail) { System.err.println("Blocking review findings detected."); kotlin.system.exitProcess(1) }
            }
        }
        else -> Unit
    }
}
