package com.claudecode.terminal

import com.claudecode.status.ClaudeSessionMonitor
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

/**
 * Listens to ClaudeSessionMonitor and matches sessions to terminal tabs
 * by CWD, updating tab icons to reflect session state.
 */
@Service(Service.Level.PROJECT)
class ClaudeTerminalStatusTracker(private val project: Project) : Disposable {

    private val monitor = ClaudeSessionMonitor.getInstance()
    private val changeListener: () -> Unit = { updateTabIcons() }

    init {
        monitor.addListener(changeListener)
    }

    private fun updateTabIcons() {
        val tabManager = ClaudeTerminalTabManager.getInstance(project)
        val sessions = monitor.sessions
        val now = System.currentTimeMillis() / 1000

        // Track which tabs have been matched by tab ID so we don't also CWD-match them
        val tabIdMatchedSessions = mutableSetOf<String>()

        for ((content, tabId, tabCwd) in tabManager.getAllTabs()) {
            // Priority 1: Match by tab ID (exact match to specific terminal tab)
            val tabIdMatch = sessions.find { it.tabId == tabId }
            if (tabIdMatch != null) {
                tabManager.updateTabIcon(content, tabIdMatch.effectiveState(now))
                tabIdMatchedSessions.add(tabIdMatch.sessionId)
                continue
            }

            // Priority 2: Fall back to CWD matching, but only for sessions without a tab ID
            // (or sessions whose tab ID didn't match any open tab)
            val cwdMatch = sessions
                .filter { it.sessionId !in tabIdMatchedSessions }
                .filter { session ->
                    val sessionCwd = canonicalizePath(session.cwd)
                    sessionCwd == tabCwd || sessionCwd.startsWith("$tabCwd\\") || sessionCwd.startsWith("$tabCwd/")
                }
                .maxByOrNull { it.cwd.length }

            if (cwdMatch != null) {
                tabManager.updateTabIcon(content, cwdMatch.effectiveState(now))
            } else {
                tabManager.clearTabIcon(content)
            }
        }
    }

    override fun dispose() {
        monitor.removeListener(changeListener)
    }

    companion object {
        fun getInstance(project: Project): ClaudeTerminalStatusTracker {
            return project.getService(ClaudeTerminalStatusTracker::class.java)
        }
    }
}
