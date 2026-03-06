package com.claudecode.terminal

import com.claudecode.status.ClaudeSessionMonitor
import com.claudecode.status.SessionState
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import javax.swing.Icon

/**
 * Listens to ClaudeSessionMonitor and matches sessions to terminal tabs
 * by CWD, updating tab icons to reflect session state.
 * Also updates the tool window sidebar icon with a badge for the worst active status.
 */
@Service(Service.Level.PROJECT)
class ClaudeTerminalStatusTracker(private val project: Project) : Disposable {

    private val monitor = ClaudeSessionMonitor.getInstance()
    private val changeListener: () -> Unit = { updateTabIcons() }

    /**
     * The base (unbadged) tool window icon, set explicitly by the factory
     * after tool window creation. Avoids timing issues with lazy capture.
     */
    var baseToolWindowIcon: Icon? = null

    /** The last badge state applied, to avoid redundant setIcon calls. */
    private var lastBadgeState: SessionState? = null

    init {
        monitor.addListener(changeListener)
    }

    private fun updateTabIcons() {
        val tabManager = ClaudeTerminalTabManager.getInstance(project)
        val sessions = monitor.sessions
        val now = System.currentTimeMillis() / 1000

        val tabIdMatchedSessions = mutableSetOf<String>()
        val matchedStates = mutableListOf<SessionState>()

        for ((content, tabId, tabCwd) in tabManager.getAllTabs()) {
            val tabIdMatch = sessions.find { it.tabId == tabId }
            if (tabIdMatch != null) {
                val state = tabIdMatch.effectiveState(now)
                tabManager.updateTabIcon(content, state)
                tabIdMatchedSessions.add(tabIdMatch.sessionId)
                matchedStates.add(state)
                continue
            }

            val cwdMatch = sessions
                .filter { it.sessionId !in tabIdMatchedSessions }
                .filter { session ->
                    val sessionCwd = canonicalizePath(session.cwd)
                    sessionCwd == tabCwd || sessionCwd.startsWith("$tabCwd\\") || sessionCwd.startsWith("$tabCwd/")
                }
                .maxByOrNull { it.cwd.length }

            if (cwdMatch != null) {
                val state = cwdMatch.effectiveState(now)
                tabManager.updateTabIcon(content, state)
                matchedStates.add(state)
            } else {
                tabManager.clearTabIcon(content)
            }
        }

        updateToolWindowBadge(matchedStates)
    }

    /**
     * Updates the tool window sidebar icon with a badge reflecting the worst active status.
     * Red (working) > Orange (waiting) > no badge.
     * Only calls setIcon when the badge state actually changes.
     */
    private fun updateToolWindowBadge(states: List<SessionState>) {
        val baseIcon = baseToolWindowIcon ?: return

        val worstState = when {
            states.any { it == SessionState.WORKING } -> SessionState.WORKING
            states.any { it == SessionState.WAITING } -> SessionState.WAITING
            else -> null
        }

        // Skip redundant setIcon calls
        if (worstState == lastBadgeState) return
        lastBadgeState = worstState

        val toolWindow = ToolWindowManager.getInstance(project)
            .getToolWindow(CLAUDE_TERMINAL_TOOL_WINDOW_ID) ?: return

        toolWindow.setIcon(
            if (worstState != null) SessionIconProvider.getBadgedIcon(baseIcon, worstState)
            else baseIcon
        )
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
