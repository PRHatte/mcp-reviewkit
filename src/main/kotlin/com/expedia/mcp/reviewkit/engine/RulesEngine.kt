package com.expedia.mcp.reviewkit.engine

import com.expedia.mcp.reviewkit.config.ReviewerConfig
import com.expedia.mcp.reviewkit.github.PullRequest
import com.expedia.mcp.reviewkit.github.PullRequestFile
import com.expedia.mcp.reviewkit.util.GlobMatcher

data class RuleFinding(val id: String, val severity: Severity, val summary: String)
enum class Severity { INFO, WARN, ERROR }

data class RuleResults(
    val infos: MutableList<RuleFinding> = mutableListOf(),
    val warnings: MutableList<RuleFinding> = mutableListOf(),
    val errors: MutableList<RuleFinding> = mutableListOf()
) {
    fun add(f: RuleFinding) = when (f.severity) {
        Severity.INFO -> infos.add(f)
        Severity.WARN -> warnings.add(f)
        Severity.ERROR -> errors.add(f)
    }
}

class RulesEngine(private val config: ReviewerConfig) {
    fun run(pr: PullRequest, files: List<PullRequestFile>): RuleResults {
        val results = RuleResults()

        if (config.rules.requireLinkedIssue) {
            val hasIssue = hasLinkedIssue(pr)
            if (!hasIssue) results.add(RuleFinding("require-linked-issue", Severity.ERROR,
                "PR must reference a linked issue (e.g., #123 or JIRA key ABC-123)."))
            else results.add(RuleFinding("require-linked-issue", Severity.INFO, "Linked issue detected."))
        }

        if (config.rules.requireTestsForSrcChanges) {
            val srcChanged = files.any { GlobMatcher.matches("src/**", it.filename) }
            if (srcChanged) {
                val hasTests = files.any {
                    GlobMatcher.matches("src/test/**", it.filename) ||
                    it.filename.endsWith("Test.kt") || it.filename.endsWith("Tests.kt")
                }
                if (!hasTests) results.add(RuleFinding("require-tests-for-src-changes", Severity.ERROR,
                    "Changes under src/** require corresponding test changes."))
                else results.add(RuleFinding("require-tests-for-src-changes", Severity.INFO, "Test changes detected."))
            }
        }

        val docGlobs = config.rules.requireDocsOnPaths
        if (docGlobs.isNotEmpty()) {
            val pathsTrigger = files.any { f -> docGlobs.any { g -> GlobMatcher.matches(g, f.filename) } }
            if (pathsTrigger) {
                val docsUpdated = files.any { f -> GlobMatcher.matches("docs/**", f.filename) || f.filename.contains("README", true) }
                val bodyHasLink = (pr.body ?: "").contains("http://") || (pr.body ?: "").contains("https://")
                if (!docsUpdated && !bodyHasLink) results.add(RuleFinding("require-docs-on-paths", Severity.WARN,
                    "Changes affect API/migrations; update docs or link to Confluence/ADR."))
                else results.add(RuleFinding("require-docs-on-paths", Severity.INFO, "Docs updated or link provided."))
            }
        }

        if (config.rules.forbidSecrets.enabled) {
            val patterns = config.rules.forbidSecrets.patterns.map { Regex(it) }
            val hits = mutableListOf<String>()
            for (f in files) {
                val patch = f.patch ?: continue
                patch.lines().forEach { line ->
                    if (line.startsWith("+")) {
                        val added = line.removePrefix("+")
                        for (p in patterns) if (p.containsMatchIn(added)) { hits.add("${f.filename} -> ${added.take(200)}"); break }
                    }
                }
            }
            if (hits.isNotEmpty()) results.add(RuleFinding("forbid-secrets", Severity.ERROR,
                "Potential secrets detected in added lines:\n- " + hits.take(5).joinToString("\n- ")))
        }

        return results
    }

    private fun hasLinkedIssue(pr: PullRequest): Boolean {
        val title = pr.title ?: ""; val body = pr.body ?: ""
        val issueRef = Regex("#[0-9]{1,7}"); val jiraRef = Regex("[A-Z][A-Z0-9]+-[0-9]{1,6}")
        return issueRef.containsMatchIn(title) || issueRef.containsMatchIn(body) ||
               jiraRef.containsMatchIn(title) || jiraRef.containsMatchIn(body)
    }
}
