package com.claudecode.terminal

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Toolbar "+" action to add a new terminal tab in the Claude Terminal tool window.
 */
class ClaudeTerminalNewTabAction : AnAction("New Claude Terminal Tab", "Open a new terminal tab", AllIcons.General.Add) {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(CLAUDE_TERMINAL_TOOL_WINDOW_ID) ?: return

        val tabManager = ClaudeTerminalTabManager.getInstance(project)
        tabManager.createTerminalTab(toolWindow, defaultBasePath(project))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
