package com.claudecode.status

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Persistent application-level settings for the Claude Code Status plugin.
 * Configurable via Settings > Tools > Claude Code Status.
 */
@Service(Service.Level.APP)
@State(name = "ClaudeCodeStatusSettings", storages = [Storage("claudeCodeStatus.xml")])
class ClaudeStatusSettings : PersistentStateComponent<ClaudeStatusSettings.State> {

    data class State(
        /** Polling interval in milliseconds for checking status files. */
        var pollIntervalMs: Long = DEFAULT_POLL_INTERVAL_MS,
        /** Seconds after which a session is considered stale. */
        var staleThresholdSeconds: Long = DEFAULT_STALE_THRESHOLD_SECONDS,
        /** Seconds after which a status file is automatically cleaned up. */
        var cleanupThresholdSeconds: Long = DEFAULT_CLEANUP_THRESHOLD_SECONDS,
        /** Custom status directory path. Empty string means default (~/.claude/terminal-status). */
        var statusDirPath: String = ""
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    val pollIntervalMs: Long get() = myState.pollIntervalMs
    val staleThresholdSeconds: Long get() = myState.staleThresholdSeconds
    val cleanupThresholdSeconds: Long get() = myState.cleanupThresholdSeconds
    val statusDirPath: String get() = myState.statusDirPath

    companion object {
        const val DEFAULT_POLL_INTERVAL_MS = 500L
        const val DEFAULT_STALE_THRESHOLD_SECONDS = 120L
        const val DEFAULT_CLEANUP_THRESHOLD_SECONDS = 600L

        fun getInstance(): ClaudeStatusSettings {
            return ApplicationManager.getApplication().getService(ClaudeStatusSettings::class.java)
        }
    }
}
