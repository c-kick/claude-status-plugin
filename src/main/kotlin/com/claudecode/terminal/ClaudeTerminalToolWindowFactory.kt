package com.claudecode.terminal

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * Factory that registers the "Claude Terminal" tool window.
 * Creates the first terminal tab at the project base path on init.
 */
class ClaudeTerminalToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Capture the base icon set by the XML `icon` attribute for badge compositing later
        val tracker = ClaudeTerminalStatusTracker.getInstance(project)
        tracker.baseToolWindowIcon = toolWindow.icon

        // Add "+" button to the tool window title bar
        val newTabAction = ClaudeTerminalNewTabAction()
        toolWindow.setTitleActions(listOf(newTabAction))

        // Create initial terminal tab at project base path
        val tabManager = ClaudeTerminalTabManager.getInstance(project)
        tabManager.createTerminalTab(toolWindow, defaultBasePath(project))
    }
}
