package com.expedia.mcp.reviewkit.mcp

data class JiraIssue(val key: String, val url: String?)

class JiraMcp(private val client: McpClient) {

    suspend fun searchIssues(jql: String, maxResults: Int = 5): List<JiraIssue> {
        val json = client.callTool("searchIssues", mapOf("jql" to jql, "maxResults" to maxResults))
        val issuesNode = if (json.has("issues")) json.get("issues") else json
        if (!issuesNode.isArray) return emptyList()
        return issuesNode.map { node ->
            val key = node.path("key").asText("")
            val selfUrl = node.path("self").asText(null)
            JiraIssue(key = key, url = selfUrl)
        }
    }

    suspend fun createIssue(projectKey: String, issueType: String, summary: String, description: String, labels: List<String>): JiraIssue {
        val json = client.callTool("createIssue", mapOf(
            "projectKey" to projectKey,
            "issueType" to issueType,
            "summary" to summary,
            "description" to description,
            "labels" to labels
        ))
        val key = json.path("key").asText("")
        val url = json.path("url").asText(null)
        return JiraIssue(key, url)
    }
}
