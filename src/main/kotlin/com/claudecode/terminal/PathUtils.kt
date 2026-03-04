package com.claudecode.terminal

import java.io.File

/** Tool window ID used in plugin.xml and action lookups. */
const val CLAUDE_TERMINAL_TOOL_WINDOW_ID = "Claude Terminal"

private val IS_WINDOWS = System.getProperty("os.name", "").lowercase().contains("win")

/**
 * Canonicalizes a file path for consistent comparison.
 * Resolves symlinks and normalizes separators.
 * Only lowercases on Windows (case-insensitive FS); preserves case on Linux/macOS.
 */
fun canonicalizePath(path: String): String {
    return try {
        val canonical = File(path).canonicalPath
        if (IS_WINDOWS) canonical.lowercase() else canonical
    } catch (_: Exception) {
        if (IS_WINDOWS) path.lowercase() else path
    }
}

/** Returns the project base path, falling back to the user's home directory. */
fun defaultBasePath(project: com.intellij.openapi.project.Project): String {
    return project.basePath ?: System.getProperty("user.home") ?: "."
}
