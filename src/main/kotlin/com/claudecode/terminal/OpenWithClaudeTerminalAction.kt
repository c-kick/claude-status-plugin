package com.claudecode.terminal

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Context menu action: "Open with Claude Terminal"
 * Opens a Claude Terminal tab at the selected directory.
 * Extends DumbAwareAction so it remains visible during IDE indexing.
 */
class OpenWithClaudeTerminalAction : DumbAwareAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val directory = if (virtualFile.isDirectory) virtualFile.path else virtualFile.parent?.path ?: return

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(CLAUDE_TERMINAL_TOOL_WINDOW_ID) ?: return
        toolWindow.show {
            val tabManager = ClaudeTerminalTabManager.getInstance(project)
            tabManager.createTerminalTab(toolWindow, directory)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null
    }
}
