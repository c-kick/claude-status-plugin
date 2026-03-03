package com.claudecode.status

import com.google.gson.JsonParser

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
 */
data class ClaudeSession(
    val sessionId: String,
    val state: SessionState,
    val cwd: String,
    val project: String,
    val timestamp: Long
) {
    companion object {
        private const val STALE_THRESHOLD_SECONDS = 120L   // 2 minutes
        private const val CLEANUP_THRESHOLD_SECONDS = 600L // 10 minutes

        /**
         * Regex that matches a backslash NOT followed by a valid JSON escape character.
         * Claude Code on Windows writes paths with unescaped backslashes (e.g., D:\_projects\...).
         * This fixes them before Gson parsing.
         */
        private val UNESCAPED_BACKSLASH = Regex("""\\(?!["\\/bfnrtu])""")

        fun fromJson(json: String): ClaudeSession? {
            return try {
                // Fix unescaped backslashes in Windows paths before parsing
                val fixedJson = UNESCAPED_BACKSLASH.replace(json, """\\\\""")
                val obj = JsonParser.parseString(fixedJson).asJsonObject
                val sessionId = obj.get("session_id")?.asString ?: return null
                val stateStr = obj.get("state")?.asString ?: return null
                val cwd = obj.get("cwd")?.asString ?: ""
                val project = obj.get("project")?.asString ?: ""
                val timestamp = obj.get("timestamp")?.asLong ?: return null

                val state = SessionState.fromJsonValue(stateStr) ?: return null
                if (state == SessionState.STALE) return null // STALE is derived, not a valid input

                ClaudeSession(sessionId, state, cwd, project, timestamp)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Returns the effective state, accounting for staleness.
     * Any session not updated within the stale threshold is considered STALE,
     * regardless of its last known state.
     */
    fun effectiveState(nowEpochSeconds: Long): SessionState {
        if ((nowEpochSeconds - timestamp) > STALE_THRESHOLD_SECONDS) {
            return SessionState.STALE
        }
        return state
    }

    fun shouldCleanup(nowEpochSeconds: Long): Boolean {
        return (nowEpochSeconds - timestamp) > CLEANUP_THRESHOLD_SECONDS
    }
}
