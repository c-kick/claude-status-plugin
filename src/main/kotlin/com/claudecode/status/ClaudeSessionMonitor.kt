package com.claudecode.status

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Application-level service that polls ~/.claude/terminal-status/ for session
 * status files and notifies registered listeners of changes.
 */
@Service(Service.Level.APP)
class ClaudeSessionMonitor : Disposable {

    private val log = Logger.getInstance(ClaudeSessionMonitor::class.java)

    private val statusDir: File = File(System.getProperty("user.home"), ".claude/terminal-status")
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("ClaudeStatusPoller", 1)
    private var pollTask: ScheduledFuture<*>? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var sessions: List<ClaudeSession> = emptyList()
        private set

    init {
        startPolling()
    }

    private fun startPolling() {
        pollTask = scheduler.scheduleWithFixedDelay(::poll, 0, 500, TimeUnit.MILLISECONDS)
    }

    private fun poll() {
        try {
            val now = System.currentTimeMillis() / 1000
            val files = statusDir.listFiles { f -> f.extension == "json" }

            if (files == null) {
                log.debug("statusDir=${statusDir.absolutePath} exists=${statusDir.exists()} files=null")
                if (sessions.isNotEmpty()) {
                    sessions = emptyList()
                    notifyListeners()
                }
                return
            }

            log.debug("Found ${files.size} json files in ${statusDir.absolutePath}")

            val allParsed = mutableListOf<Pair<File, ClaudeSession>>()

            for (file in files) {
                val json = try {
                    file.readText()
                } catch (_: Exception) {
                    continue
                }

                val session = ClaudeSession.fromJson(json)
                if (session == null) {
                    log.debug("Parse failed for ${file.name}")
                    continue
                }

                // Auto-cleanup files past the cleanup threshold
                if (session.shouldCleanup(now)) {
                    try { file.delete() } catch (_: Exception) {}
                    continue
                }

                allParsed.add(file to session)
            }

            // Deduplicate: when multiple files share the same canonical cwd,
            // keep only the newest. This handles session resume creating two
            // session IDs and orphans from crashed sessions in the same terminal.
            val byCwd = allParsed.groupBy { canonicalizeCwd(it.second.cwd) }
            val newSessions = mutableListOf<ClaudeSession>()

            for ((_, entries) in byCwd) {
                val sorted = entries.sortedByDescending { it.second.timestamp }
                // Keep the newest
                newSessions.add(sorted.first().second)
                // Delete older duplicates
                for (i in 1 until sorted.size) {
                    try { sorted[i].first.delete() } catch (_: Exception) {}
                }
            }

            // Sort by project name for stable ordering
            newSessions.sortBy { it.project }

            if (newSessions != sessions) {
                log.info("Sessions changed: ${newSessions.size} sessions, ${listeners.size} listeners")
                sessions = newSessions
                notifyListeners()
            }
        } catch (e: Exception) {
            log.warn("Error polling Claude status directory", e)
        }
    }

    /**
     * Canonicalizes a CWD path for consistent deduplication.
     * Lowercases for case-insensitive matching on Windows.
     */
    private fun canonicalizeCwd(path: String): String {
        return try {
            File(path).canonicalPath.lowercase()
        } catch (_: Exception) {
            path.lowercase()
        }
    }

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        ApplicationManager.getApplication().invokeLater {
            for (listener in listeners) {
                try {
                    listener()
                } catch (e: Exception) {
                    log.warn("Error in status listener", e)
                }
            }
        }
    }

    override fun dispose() {
        pollTask?.cancel(false)
        scheduler.shutdownNow()
        listeners.clear()
    }

    companion object {
        fun getInstance(): ClaudeSessionMonitor {
            return ApplicationManager.getApplication().getService(ClaudeSessionMonitor::class.java)
        }
    }
}
