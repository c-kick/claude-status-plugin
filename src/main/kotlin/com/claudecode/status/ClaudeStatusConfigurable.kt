package com.claudecode.status

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows

/**
 * Settings UI panel for Claude Code Status.
 * Accessible via Settings > Tools > Claude Code Status.
 */
class ClaudeStatusConfigurable : BoundConfigurable("Claude Code Status") {

    private val settings = ClaudeStatusSettings.getInstance()

    // Mutable proxies for the UI bindings (work with Int for text fields)
    private var pollIntervalMs: Int = settings.pollIntervalMs.toInt()
    private var staleThresholdSeconds: Int = settings.staleThresholdSeconds.toInt()
    private var cleanupThresholdSeconds: Int = settings.cleanupThresholdSeconds.toInt()
    private var statusDirPath: String = settings.statusDirPath

    override fun createPanel(): DialogPanel = panel {
        group("Polling") {
            row("Poll interval (ms):") {
                intTextField(100..5000)
                    .bindIntText(::pollIntervalMs)
                    .comment("How often to check for status file changes (100-5000ms, default 500)")
            }
        }
        group("Session Thresholds") {
            row("Stale threshold (seconds):") {
                intTextField(10..3600)
                    .bindIntText(::staleThresholdSeconds)
                    .comment("Seconds before a session is marked stale (default 120)")
            }
            row("Cleanup threshold (seconds):") {
                intTextField(60..86400)
                    .bindIntText(::cleanupThresholdSeconds)
                    .comment("Seconds before a status file is auto-deleted (default 600)")
            }
        }
        group("Status Directory") {
            row("Custom path:") {
                textField()
                    .bindText(::statusDirPath)
                    .comment("Leave empty to use default (~/.claude/terminal-status)")
                    .resizableColumn()
            }.layout(com.intellij.ui.dsl.builder.RowLayout.PARENT_GRID)
        }
    }

    override fun apply() {
        super.apply()
        val state = settings.state
        state.pollIntervalMs = pollIntervalMs.toLong()
        state.staleThresholdSeconds = staleThresholdSeconds.toLong()
        state.cleanupThresholdSeconds = cleanupThresholdSeconds.toLong()
        state.statusDirPath = statusDirPath
        settings.loadState(state)
    }

    override fun reset() {
        pollIntervalMs = settings.pollIntervalMs.toInt()
        staleThresholdSeconds = settings.staleThresholdSeconds.toInt()
        cleanupThresholdSeconds = settings.cleanupThresholdSeconds.toInt()
        statusDirPath = settings.statusDirPath
        super.reset()
    }
}
