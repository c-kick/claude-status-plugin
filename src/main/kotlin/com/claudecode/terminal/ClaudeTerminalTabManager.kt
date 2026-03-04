package com.claudecode.terminal

import com.claudecode.status.SessionState
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.terminal.JBTerminalSystemSettingsProviderBase
import com.intellij.terminal.JBTerminalWidget
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import org.jetbrains.plugins.terminal.TerminalProjectOptionsProvider
import java.awt.event.KeyEvent
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level service that creates terminal tabs inside the Claude Terminal
 * tool window, tracks CWD-to-tab mapping, and updates tab icons.
 */
@Service(Service.Level.PROJECT)
class ClaudeTerminalTabManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(ClaudeTerminalTabManager::class.java)

    /** Maps Content to (tabId, canonicalCwd) for each tab. */
    private val tabRegistry = ConcurrentHashMap<Content, Pair<String, String>>()

    /** Tracks PTY processes so they can be killed on tab close. */
    private val tabProcesses = ConcurrentHashMap<Content, PtyProcess>()

    /** Counter for disambiguating tab names with the same directory basename. */
    private val tabNameCounts = ConcurrentHashMap<String, Int>()

    /**
     * Creates a new terminal tab in the given tool window at the specified directory.
     */
    fun createTerminalTab(toolWindow: ToolWindow, directory: String): Content? {
        val canonicalDir = File(directory).canonicalPath
        val tabName = disambiguateTabName(canonicalDir)
        val tabId = UUID.randomUUID().toString()

        val tabDisposable = Disposer.newDisposable(this, "ClaudeTerminalTab:$tabName")

        val settingsProvider = JBTerminalSystemSettingsProviderBase()
        val widget: JBTerminalWidget
        try {
            widget = JBTerminalWidget(project, settingsProvider, tabDisposable)
        } catch (e: Exception) {
            log.warn("Failed to create terminal widget", e)
            Disposer.dispose(tabDisposable)
            return null
        }

        val content = toolWindow.contentManager.factory.createContent(
            widget.component,
            tabName,
            false
        )
        content.putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
        content.isCloseable = true
        content.setDisposer(tabDisposable)

        tabRegistry[content] = tabId to canonicalizePath(canonicalDir)

        // Intercept Escape so the IDE doesn't steal focus from the terminal.
        // Instead, forward the ESC byte to the PTY process.
        DumbAwareAction.create {
            tabProcesses[content]?.let { process ->
                try {
                    process.outputStream.write(27) // ESC
                    process.outputStream.flush()
                } catch (e: Exception) {
                    log.debug("Failed to forward ESC to PTY (process may have exited): ${e.message}")
                }
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyEvent.VK_ESCAPE),
            widget.component,
            tabDisposable
        )

        // Tie the listener lifecycle to the tab's disposable so it's cleaned up
        // even if ContentManager is disposed without firing contentRemoved.
        val listener = object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content === content) {
                    tabRegistry.remove(content)
                    destroyProcess(content)
                    toolWindow.contentManager.removeContentManagerListener(this)
                }
            }
        }
        toolWindow.contentManager.addContentManagerListener(listener)
        Disposer.register(tabDisposable) {
            toolWindow.contentManager.removeContentManagerListener(listener)
            tabRegistry.remove(content)
            destroyProcess(content)
        }

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        // Create PTY process on pooled thread, then wire up widget on EDT
        val disposed = AtomicBoolean(false)
        Disposer.register(tabDisposable) { disposed.set(true) }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val (connector, process) = createTtyConnector(canonicalDir, tabId)
                tabProcesses[content] = process
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        widget.createTerminalSession(connector)
                        widget.start()
                    } else {
                        destroyProcessGracefully(process)
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to start terminal session", e)
            }
        }

        return content
    }

    /**
     * Generates a unique tab name for the given directory.
     * If a tab with the same basename already exists, appends the parent directory
     * for disambiguation (e.g. "src" → "frontend/src").
     */
    private fun disambiguateTabName(canonicalDir: String): String {
        val dirFile = File(canonicalDir)
        val baseName = dirFile.name

        // Check if any existing tab already has this base name
        val existingWithSameName = tabRegistry.values.any { (_, cwd) ->
            File(cwd).name.equals(baseName, ignoreCase = true)
        }

        return if (existingWithSameName) {
            val parentName = dirFile.parentFile?.name ?: ""
            if (parentName.isNotEmpty()) "$parentName/${baseName}" else baseName
        } else {
            baseName
        }
    }

    private fun createTtyConnector(workingDirectory: String, tabId: String): Pair<com.jediterm.terminal.TtyConnector, PtyProcess> {
        val shell = getConfiguredShell()
        val env = System.getenv().toMutableMap()
        env["TERM"] = "xterm-256color"
        env["CLAUDE_TERMINAL_TAB_ID"] = tabId
        // Strip Claude Code env vars so `claude` can be launched inside our terminal.
        // Remove all known prefixes to handle future additions.
        env.keys.removeAll { it.startsWith("CLAUDECODE") || it.startsWith("CLAUDE_CODE") }

        val process = PtyProcessBuilder(arrayOf(shell))
            .setDirectory(workingDirectory)
            .setEnvironment(env)
            .setConsole(false)
            .setInitialColumns(120)
            .setInitialRows(30)
            .start()

        val connector = com.intellij.terminal.pty.PtyProcessTtyConnector(process, StandardCharsets.UTF_8)
        return connector to process
    }

    /**
     * Gracefully shuts down a PTY process: SIGTERM first, then SIGKILL after timeout.
     * This allows the shell to run cleanup traps and propagate signals to child
     * processes (like running `claude` instances).
     */
    private fun destroyProcessGracefully(process: PtyProcess) {
        if (!process.isAlive) return
        try {
            process.destroy() // SIGTERM
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly() // SIGKILL after 3s
            }
        } catch (_: Exception) {
            try { process.destroyForcibly() } catch (_: Exception) {}
        }
    }

    private fun destroyProcess(content: Content) {
        tabProcesses.remove(content)?.let { process ->
            destroyProcessGracefully(process)
        }
    }

    private fun getConfiguredShell(): String {
        return try {
            TerminalProjectOptionsProvider.getInstance(project).shellPath
        } catch (_: Exception) {
            defaultShellFallback()
        }
    }

    private fun defaultShellFallback(): String {
        val os = System.getProperty("os.name", "").lowercase()
        if (os.contains("win")) return "powershell.exe"
        return System.getenv("SHELL") ?: "/bin/bash"
    }

    fun updateTabIcon(content: Content, state: SessionState) {
        content.icon = SessionIconProvider.getIcon(state)
    }

    fun clearTabIcon(content: Content) {
        content.icon = null
    }

    fun getAllTabs(): List<Triple<Content, String, String>> {
        return tabRegistry.entries.map { Triple(it.key, it.value.first, it.value.second) }
    }

    override fun dispose() {
        for (content in tabProcesses.keys.toList()) {
            destroyProcess(content)
        }
        tabRegistry.clear()
        tabNameCounts.clear()
    }

    companion object {
        fun getInstance(project: Project): ClaudeTerminalTabManager {
            return project.getService(ClaudeTerminalTabManager::class.java)
        }
    }
}
