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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Project-level service that creates terminal tabs inside the Claude Terminal
 * tool window, tracks CWD-to-tab mapping, and updates tab icons.
 */
@Service(Service.Level.PROJECT)
class ClaudeTerminalTabManager(private val project: Project) : Disposable {

    private val log = Logger.getInstance(ClaudeTerminalTabManager::class.java)

    /** Maps Content to the canonical lowercase CWD of the tab. */
    private val tabRegistry = ConcurrentHashMap<Content, String>()

    /** Tracks PTY processes so they can be killed on tab close. */
    private val tabProcesses = ConcurrentHashMap<Content, PtyProcess>()

    /**
     * Creates a new terminal tab in the given tool window at the specified directory.
     */
    fun createTerminalTab(toolWindow: ToolWindow, directory: String): Content? {
        val canonicalDir = File(directory).canonicalPath
        val tabName = File(canonicalDir).name

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

        tabRegistry[content] = canonicalizePath(canonicalDir)

        // Intercept Escape so the IDE doesn't steal focus from the terminal.
        // Instead, forward the ESC byte to the PTY process.
        DumbAwareAction.create {
            tabProcesses[content]?.let { process ->
                if (process.isAlive) {
                    try {
                        process.outputStream.write(27) // ESC
                        process.outputStream.flush()
                    } catch (_: Exception) {}
                }
            }
        }.registerCustomShortcutSet(
            CustomShortcutSet(KeyEvent.VK_ESCAPE),
            widget.component,
            tabDisposable
        )

        toolWindow.contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                if (event.content === content) {
                    tabRegistry.remove(content)
                    destroyProcess(content)
                    toolWindow.contentManager.removeContentManagerListener(this)
                }
            }
        })

        toolWindow.contentManager.addContent(content)
        toolWindow.contentManager.setSelectedContent(content)

        // Create PTY process on pooled thread, then wire up widget on EDT
        val disposed = AtomicBoolean(false)
        Disposer.register(tabDisposable) { disposed.set(true) }
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val (connector, process) = createTtyConnector(canonicalDir)
                tabProcesses[content] = process
                ApplicationManager.getApplication().invokeLater {
                    if (!disposed.get()) {
                        widget.createTerminalSession(connector)
                        widget.start()
                    } else {
                        process.destroyForcibly()
                    }
                }
            } catch (e: Exception) {
                log.warn("Failed to start terminal session", e)
            }
        }

        return content
    }

    private fun createTtyConnector(workingDirectory: String): Pair<com.jediterm.terminal.TtyConnector, PtyProcess> {
        val shell = getConfiguredShell()
        val env = System.getenv().toMutableMap()
        env["TERM"] = "xterm-256color"
        // Strip Claude Code env vars so `claude` can be launched inside our terminal
        env.remove("CLAUDECODE")
        env.remove("CLAUDE_CODE")

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

    private fun destroyProcess(content: Content) {
        tabProcesses.remove(content)?.let { process ->
            if (process.isAlive) {
                process.destroyForcibly()
            }
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

    fun getAllTabs(): List<Pair<Content, String>> {
        return tabRegistry.entries.map { it.key to it.value }
    }

    override fun dispose() {
        for (content in tabProcesses.keys.toList()) {
            destroyProcess(content)
        }
        tabRegistry.clear()
    }

    companion object {
        fun getInstance(project: Project): ClaudeTerminalTabManager {
            return project.getService(ClaudeTerminalTabManager::class.java)
        }
    }
}
