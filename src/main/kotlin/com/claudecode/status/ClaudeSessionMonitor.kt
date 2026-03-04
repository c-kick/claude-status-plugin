package com.claudecode.status

import com.claudecode.terminal.canonicalizePath
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Application-level service that monitors ~/.claude/terminal-status/ for session
 * status files and notifies registered listeners of changes.
 *
 * Uses Java WatchService (inotify/kqueue/ReadDirectoryChangesW) for efficient
 * change detection, with a fallback polling interval to catch any missed events.
 */
@Service(Service.Level.APP)
class ClaudeSessionMonitor : Disposable {

    private val log = Logger.getInstance(ClaudeSessionMonitor::class.java)
    private val settings = ClaudeStatusSettings.getInstance()

    private val statusDir: File
        get() {
            val custom = settings.statusDirPath
            return if (custom.isNotBlank()) File(custom)
            else File(System.getProperty("user.home"), ".claude/terminal-status")
        }

    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("ClaudeStatusPoller", 2)
    private var pollTask: ScheduledFuture<*>? = null
    private var watchTask: ScheduledFuture<*>? = null
    private var watchService: WatchService? = null
    private var watchKey: WatchKey? = null

    private val listeners = CopyOnWriteArrayList<() -> Unit>()

    @Volatile
    var sessions: List<ClaudeSession> = emptyList()
        private set

    init {
        startWatching()
    }

    private fun startWatching() {
        // Start the WatchService for efficient filesystem notifications
        scheduler.submit(::initWatchService)

        // Fallback poll to catch missed WatchService events
        // (e.g. on NFS or platforms where WatchService degrades to polling)
        pollTask = scheduler.scheduleWithFixedDelay(
            ::poll, 0, settings.pollIntervalMs, TimeUnit.MILLISECONDS
        )
    }

    /**
     * Initializes the WatchService on the status directory.
     * Creates the directory if it doesn't exist yet.
     */
    private fun initWatchService() {
        try {
            val dir = statusDir
            if (!dir.exists()) {
                dir.mkdirs()
            }
            val path = dir.toPath()
            val ws = FileSystems.getDefault().newWatchService()
            val key = path.register(
                ws,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE
            )
            watchService = ws
            watchKey = key

            // Run a blocking watch loop on the scheduler
            watchTask = scheduler.submit(::watchLoop)
            log.info("WatchService initialized on ${dir.absolutePath}")
        } catch (e: Exception) {
            log.info("WatchService unavailable, relying on polling: ${e.message}")
        }
    }

    /**
     * Blocking loop that waits for WatchService events and triggers a poll.
     */
    private fun watchLoop() {
        val ws = watchService ?: return
        try {
            while (!Thread.currentThread().isInterrupted) {
                val key = ws.take() // blocks until event
                // Drain all events
                var hasJsonChange = false
                for (event in key.pollEvents()) {
                    val kind = event.kind()
                    if (kind == StandardWatchEventKinds.OVERFLOW) continue
                    val filename = (event.context() as? Path)?.toString() ?: continue
                    if (filename.endsWith(".json") && !filename.endsWith(".tmp")) {
                        hasJsonChange = true
                    }
                }
                if (hasJsonChange) {
                    poll()
                }
                if (!key.reset()) {
                    log.info("WatchKey invalidated, falling back to polling only")
                    break
                }
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            log.warn("WatchService loop error, falling back to polling only", e)
        }
    }

    private fun poll() {
        try {
            val now = System.currentTimeMillis() / 1000
            val dir = statusDir
            val files = dir.listFiles { f -> f.extension == "json" }

            if (files == null) {
                log.debug("statusDir=${dir.absolutePath} exists=${dir.exists()} files=null")
                if (sessions.isNotEmpty()) {
                    sessions = emptyList()
                    notifyListeners()
                }
                return
            }

            log.debug("Found ${files.size} json files in ${dir.absolutePath}")

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

            // Group by canonical CWD for deduplication.
            // Keep ALL sessions that have distinct tab IDs (they represent different
            // terminal tabs intentionally running in the same directory).
            // Only deduplicate sessions that share BOTH a CWD and lack a tab ID
            // (orphans from crashed/resumed sessions).
            val newSessions = mutableListOf<ClaudeSession>()
            val byCwd = allParsed.groupBy { canonicalizePath(it.second.cwd) }

            for ((_, entries) in byCwd) {
                val (withTabId, withoutTabId) = entries.partition { it.second.tabId != null }

                // Keep all sessions that have a tab ID (distinct terminal tabs)
                newSessions.addAll(withTabId.map { it.second })

                // For sessions without a tab ID, keep only the newest per CWD
                // and clean up the rest (these are true orphans)
                if (withoutTabId.isNotEmpty()) {
                    val sorted = withoutTabId.sortedByDescending { it.second.timestamp }
                    newSessions.add(sorted.first().second)
                    for (i in 1 until sorted.size) {
                        try { sorted[i].first.delete() } catch (_: Exception) {}
                    }
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
        watchTask?.cancel(true) // interrupt blocking take()
        try { watchKey?.cancel() } catch (_: Exception) {}
        try { watchService?.close() } catch (_: Exception) {}
        scheduler.shutdownNow()
        listeners.clear()
    }

    companion object {
        fun getInstance(): ClaudeSessionMonitor {
            return ApplicationManager.getApplication().getService(ClaudeSessionMonitor::class.java)
        }
    }
}
