package com.expedia.mcp.reviewkit.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File

data class ConfluenceConfig(
    val enabled: Boolean = false,
    val spaces: List<String> = emptyList(),
    val queries: List<String> = emptyList(),
    val maxSnippets: Int = 6
)

data class JiraConfig(
    val enabled: Boolean = false,
    val projectKey: String = "ENG",
    val issueType: String = "Task",
    val defaultLabels: List<String> = listOf("mcp-reviewkit", "{repo}"),
    val commands: List<String> = listOf("/create-jira", "/jira"),
    val dedupeByPrUrl: Boolean = true,
    val dedupeByTitlePrefix: Boolean = true
)

data class ForbidSecretsConfig(
    val enabled: Boolean = true,
    val patterns: List<String> = listOf(
        "(?i)aws(.{0,20})?(access|secret)_?key",
        "AIzaSy[a-zA-Z0-9_-]{35}",
        "ghp_[A-Za-z0-9]{36}",
        "(?i)private[_-]?key",
        "(?i)password\\s*[:=]"
    )
)

data class RulesConfig(
    val requireLinkedIssue: Boolean = true,
    val requireTestsForSrcChanges: Boolean = true,
    val requireDocsOnPaths: List<String> = listOf("api/**", "migrations/**"),
    val forbidSecrets: ForbidSecretsConfig = ForbidSecretsConfig()
)

data class LlmPrompt(val id: String, val text: String)
data class LlmConfig(val enabled: Boolean = false, val prompts: List<LlmPrompt> = emptyList())

data class ReviewerConfig(
    val confluence: ConfluenceConfig = ConfluenceConfig(),
    val jira: JiraConfig = JiraConfig(),
    val rules: RulesConfig = RulesConfig(),
    val llm: LlmConfig = LlmConfig()
)

object ConfigLoader {
    private val yaml = jacksonObjectMapper(YAMLFactory())
        .registerModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun load(path: String?): ReviewerConfig {
        if (path.isNullOrBlank()) return ReviewerConfig()
        val file = File(path)
        if (!file.exists()) return ReviewerConfig()
        return yaml.readValue(file)
    }
}
