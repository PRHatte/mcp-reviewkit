package com.expedia.mcp.reviewkit.mcp

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

data class McpConfluenceSnippet(val title: String, val url: String, val excerpt: String?)

class ConfluenceMcp(private val client: McpClient) {
    private val mapper = jacksonObjectMapper()

    suspend fun search(query: String, limit: Int = 5): List<McpConfluenceSnippet> {
        val json = try {
            client.callTool("search", mapOf("query" to query, "limit" to limit))
        } catch (e: Exception) {
            client.callTool("searchCql", mapOf("cql" to query, "limit" to limit))
        }
        val arr = if (json.isArray) json else json.path("results")
        return try { mapper.readValue(arr.toString()) } catch (_: Exception) {
            arr.map { node ->
                val title = node.path("title").asText("")
                val url = node.path("url").asText("")
                val excerpt = node.path("excerpt").asText(null)
                McpConfluenceSnippet(title, url, if (excerpt.isNullOrBlank()) null else excerpt)
            }
        }
    }
}
