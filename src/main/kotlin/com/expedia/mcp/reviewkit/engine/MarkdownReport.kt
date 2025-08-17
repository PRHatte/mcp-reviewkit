package com.expedia.mcp.reviewkit.engine

object MarkdownReportComposer {
    private const val MARKER = "<!-- mcp-reviewkit -->"

    fun build(findings: RuleResults, llmText: String?): String {
        val sb = StringBuilder()
        sb.appendLine(MARKER)
        sb.appendLine("**PR Review Summary**")
        sb.appendLine("")
        if (findings.errors.isNotEmpty()) sb.appendLine("- **errors**: ${findings.errors.size}")
        if (findings.warnings.isNotEmpty()) sb.appendLine("- **warnings**: ${findings.warnings.size}")
        if (findings.infos.isNotEmpty()) sb.appendLine("- **infos**: ${findings.infos.size}")
        sb.appendLine("")
        if (findings.errors.isNotEmpty()) { sb.appendLine("### Errors"); findings.errors.forEach { sb.appendLine("- ${it.summary}") }; sb.appendLine("") }
        if (findings.warnings.isNotEmpty()) { sb.appendLine("### Warnings"); findings.warnings.forEach { sb.appendLine("- ${it.summary}") }; sb.appendLine("") }
        if (findings.infos.isNotEmpty()) { sb.appendLine("### Info"); findings.infos.forEach { sb.appendLine("- ${it.summary}") }; sb.appendLine("") }
        if (!llmText.isNullOrBlank()) { sb.appendLine("### LLM Assessment"); sb.appendLine(llmText.trim()); sb.appendLine("") }
        return sb.toString().trim()
    }

    fun marker(): String = MARKER
}
