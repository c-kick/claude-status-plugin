package com.claudecode.terminal

import java.io.File

/** Tool window ID used in plugin.xml and action lookups. */
const val CLAUDE_TERMINAL_TOOL_WINDOW_ID = "Claude Terminal"

/**
 * Canonicalizes a file path for case-insensitive comparison.
 * Resolves symlinks, normalizes separators, and lowercases for Windows compatibility.
 */
fun canonicalizePath(path: String): String {
    return try {
        File(path).canonicalPath.lowercase()
    } catch (_: Exception) {
        path.lowercase()
    }
}

/** Returns the project base path, falling back to the user's home directory. */
fun defaultBasePath(project: com.intellij.openapi.project.Project): String {
    return project.basePath ?: System.getProperty("user.home")
}
