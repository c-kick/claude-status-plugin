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

        val sessionStates = sessions.map { session ->
            canonicalizePath(session.cwd) to session.effectiveState(now)
        }

        for ((content, tabCwd) in tabManager.getAllTabs()) {
            // Match sessions whose CWD is equal to or a subdirectory of the tab CWD.
            // If multiple match, pick the one with the longest (most specific) CWD.
            val match = sessionStates
                .filter { (sessionCwd, _) ->
                    sessionCwd == tabCwd || sessionCwd.startsWith("$tabCwd\\") || sessionCwd.startsWith("$tabCwd/")
                }
                .maxByOrNull { (sessionCwd, _) -> sessionCwd.length }

            if (match != null) {
                tabManager.updateTabIcon(content, match.second)
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
