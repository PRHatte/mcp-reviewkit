package com.expedia.mcp.reviewkit.github

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequest(val number: Int, val title: String? = null, val body: String? = null, val html_url: String? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PullRequestFile(
    val filename: String,
    val status: String? = null,
    val additions: Int? = null,
    val deletions: Int? = null,
    val changes: Int? = null,
    val patch: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class IssueComment(val id: Long, val body: String? = null)
