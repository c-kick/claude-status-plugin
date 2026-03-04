package com.claudecode.status

/**
 * Represents the state of a Claude Code terminal session.
 */
enum class SessionState(val displayName: String, val jsonValue: String) {
    WORKING("Working", "working"),
    WAITING("Needs input", "waiting"),
    IDLE("Idle", "idle"),
    STALE("Stale", "stale");

    companion object {
        private val byJsonValue = entries.associateBy { it.jsonValue }
        fun fromJsonValue(value: String): SessionState? = byJsonValue[value]
    }
}

/**
 * Data model for a single Claude Code session, parsed from a JSON status file.
 *
 * @property sessionId Unique session identifier
 * @property state Current session state
 * @property cwd Working directory of the session
 * @property project Project name (basename of cwd)
 * @property timestamp Unix epoch seconds when state was last updated
 * @property tabId Optional tab ID for exact tab matching
 */
data class ClaudeSession(
    val sessionId: String,
    val state: SessionState,
    val cwd: String,
    val project: String,
    val timestamp: Long,
    val tabId: String? = null
) {
    companion object {
        /**
         * Parses a JSON status file into a ClaudeSession.
         *
         * Uses a minimal hand-rolled JSON parser instead of Gson to:
         * - Eliminate the external dependency
         * - Properly handle Windows paths by treating non-standard escape sequences
         *   (like \D, \P from unescaped backslashes) as literal backslash + char
         * - Avoid the UNESCAPED_BACKSLASH regex which could corrupt paths containing
         *   substrings like \new (\n), \temp (\t), \bin (\b), \fonts (\f), \runtime (\r)
         */
        fun fromJson(json: String): ClaudeSession? {
            return try {
                val fields = parseSimpleJson(json) ?: return null
                val sessionId = fields["session_id"] ?: return null
                val stateStr = fields["state"] ?: return null
                val cwd = fields["cwd"] ?: ""
                val project = fields["project"] ?: ""
                val timestampStr = fields["timestamp"] ?: return null
                val timestamp = timestampStr.toLongOrNull() ?: return null
                val tabId = fields["tab_id"]?.ifEmpty { null }

                val state = SessionState.fromJsonValue(stateStr) ?: return null
                if (state == SessionState.STALE) return null // STALE is derived, not a valid input

                ClaudeSession(sessionId, state, cwd, project, timestamp, tabId)
            } catch (_: Exception) {
                null
            }
        }

        /**
         * Minimal JSON parser for flat objects with string and number values.
         * Returns a map of field name to string value, or null if parsing fails.
         */
        private fun parseSimpleJson(json: String): Map<String, String>? {
            val trimmed = json.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

            val result = mutableMapOf<String, String>()
            var i = 1 // skip opening brace

            while (i < trimmed.length - 1) {
                // skip whitespace and commas
                while (i < trimmed.length - 1 && (trimmed[i].isWhitespace() || trimmed[i] == ',')) i++
                if (i >= trimmed.length - 1) break

                // parse key
                if (trimmed[i] != '"') return null
                val (key, keyEnd) = parseJsonString(trimmed, i) ?: return null
                i = keyEnd

                // skip colon
                while (i < trimmed.length && trimmed[i].isWhitespace()) i++
                if (i >= trimmed.length || trimmed[i] != ':') return null
                i++
                while (i < trimmed.length && trimmed[i].isWhitespace()) i++

                // parse value (string or number/literal)
                if (i >= trimmed.length) return null
                if (trimmed[i] == '"') {
                    val (value, valueEnd) = parseJsonString(trimmed, i) ?: return null
                    result[key] = value
                    i = valueEnd
                } else {
                    // number, boolean, or null — read until comma, whitespace, or }
                    val start = i
                    while (i < trimmed.length && trimmed[i] != ',' && trimmed[i] != '}' && !trimmed[i].isWhitespace()) i++
                    result[key] = trimmed.substring(start, i)
                }
            }

            return result
        }

        /**
         * Parses a JSON string starting at position [start] (which must be '"').
         * Returns the unescaped string value and the position after the closing quote.
         *
         * Non-standard escape sequences (e.g. \D from unescaped Windows paths in
         * legacy status files) are treated as literal backslash + character rather
         * than causing a parse failure.
         */
        private fun parseJsonString(json: String, start: Int): Pair<String, Int>? {
            if (start >= json.length || json[start] != '"') return null
            val sb = StringBuilder()
            var i = start + 1
            while (i < json.length) {
                val c = json[i]
                if (c == '"') return sb.toString() to (i + 1)
                if (c == '\\') {
                    i++
                    if (i >= json.length) return null
                    when (json[i]) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            if (i + 4 >= json.length) return null
                            val hex = json.substring(i + 1, i + 5)
                            sb.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> {
                            // Non-standard escape: treat as literal backslash + char.
                            // This gracefully handles legacy status files where Windows
                            // paths were written without proper JSON escaping.
                            sb.append('\\')
                            sb.append(json[i])
                        }
                    }
                } else {
                    sb.append(c)
                }
                i++
            }
            return null // unterminated string
        }
    }

    /**
     * Returns the effective state, accounting for staleness.
     * Uses the configured stale threshold from settings.
     */
    fun effectiveState(nowEpochSeconds: Long): SessionState {
        val threshold = ClaudeStatusSettings.getInstance().staleThresholdSeconds
        if ((nowEpochSeconds - timestamp) > threshold) {
            return SessionState.STALE
        }
        return state
    }

    fun shouldCleanup(nowEpochSeconds: Long): Boolean {
        val threshold = ClaudeStatusSettings.getInstance().cleanupThresholdSeconds
        return (nowEpochSeconds - timestamp) > threshold
    }
}
