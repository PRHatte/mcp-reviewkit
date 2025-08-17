package com.expedia.mcp.reviewkit.mcp

import com.expedia.mcp.reviewkit.github.IssueComment
import com.expedia.mcp.reviewkit.github.PullRequest
import com.expedia.mcp.reviewkit.github.PullRequestFile
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

class GitHubMcp(private val client: McpClient) {
    private val mapper = jacksonObjectMapper()

    suspend fun getPullRequest(owner: String, repo: String, number: Int): PullRequest {
        val json = client.callTool("getPullRequest", mapOf("owner" to owner, "repo" to repo, "number" to number))
        val node = if (json.has("number") || json.has("title")) json else json.path("pullRequest")
        return mapper.readValue(node.toString())
    }

    suspend fun listPullRequestFiles(owner: String, repo: String, number: Int): List<PullRequestFile> {
        val json = client.callTool("listPullRequestFiles", mapOf("owner" to owner, "repo" to repo, "number" to number))
        val arr = if (json.isArray) json else json.path("files")
        return mapper.readValue(arr.toString())
    }

    suspend fun listIssueComments(owner: String, repo: String, issueNumber: Int): List<IssueComment> {
        val json = client.callTool("listIssueComments", mapOf("owner" to owner, "repo" to repo, "issue_number" to issueNumber))
        val arr = if (json.isArray) json else json.path("comments")
        return mapper.readValue(arr.toString())
    }

    suspend fun createIssueComment(owner: String, repo: String, issueNumber: Int, bodyText: String): IssueComment {
        val json = client.callTool("createIssueComment", mapOf("owner" to owner, "repo" to repo, "issue_number" to issueNumber, "body" to bodyText))
        return mapper.readValue(json.toString())
    }

    suspend fun updateIssueComment(owner: String, repo: String, commentId: Long, bodyText: String): IssueComment {
        val json = client.callTool("updateIssueComment", mapOf("owner" to owner, "repo" to repo, "comment_id" to commentId, "body" to bodyText))
        return mapper.readValue(json.toString())
    }
}
