package com.expedia.mcp.reviewkit.util

import java.util.regex.Pattern

object GlobMatcher {
    fun matches(glob: String, path: String): Boolean {
        val regex = globToRegex(glob)
        return Pattern.compile(regex).matcher(path).matches()
    }
    fun anyMatch(globs: List<String>, path: String): Boolean = globs.any { matches(it, path) }
    private fun globToRegex(glob: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            val c = glob[i]
            when (c) {
                '*' -> {
                    val isDouble = i + 1 < glob.length && glob[i + 1] == '*'
                    if (isDouble) {
                        val isSlashAfter = i + 2 < glob.length && glob[i + 2] == '/'
                        if (isSlashAfter) { sb.append("(?:.*/)?"); i += 3 } else { sb.append(".*"); i += 2 }
                    } else { sb.append("[^/]*"); i += 1 }
                    continue
                }
                '?' -> sb.append('.')
                '.', '(', ')', '+', '|', '^', '$', '@', '%' -> sb.append('\\').append(c)
                else -> sb.append(c)
            }
            i += 1
        }
        return "^$sb$"
    }
}
