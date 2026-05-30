package com.mangareader.data

import android.os.FileObserver
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.io.File

/**
 * Observes a manga root directory for file changes (create/delete/move/modify)
 * and emits refresh signals via [changes] Flow.
 *
 * Monitors both the root level (new manga dirs) and one level of chapter subdirectories.
 * Debounces events by [debounceMs] to avoid rapid-fire refreshes.
 *
 * Lifecycle: call [start] to begin, [stop] to release resources.
 */
class MangaFileObserver(
    @Volatile private var rootDir: File,
    private val debounceMs: Long = 500L,
) {
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: Flow<Unit> = _changes.asSharedFlow()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var debounceJob: Job? = null

    // Mask covering create, delete, move, modify events
    private val eventMask = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.MODIFY or
            FileObserver.CLOSE_WRITE

    // Root-level observer (manga dirs appear/disappear)
    private var rootObserver: FileObserver? = null

    // Per-manga-dir observers keyed by absolute path
    private val chapterObservers = mutableMapOf<String, FileObserver>()

    @Synchronized
    fun start() {
        if (rootObserver != null) return
        if (scope.isActive.not()) {
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        }
        Timber.d("MangaFileObserver: starting on ${rootDir.absolutePath}")
        rootObserver = createObserver(rootDir).also { it.startWatching() }
        observeChapterDirs()
    }

    @Synchronized
    fun stop() {
        Timber.d("MangaFileObserver: stopping")
        rootObserver?.stopWatching()
        rootObserver = null
        chapterObservers.values.forEach { it.stopWatching() }
        chapterObservers.clear()
        debounceJob?.cancel()
        debounceJob = null
        scope.cancel()
    }

    @Synchronized
    fun updateRoot(newRoot: File) {
        Timber.d("MangaFileObserver: updating root to ${newRoot.absolutePath}")
        rootDir = newRoot
    }

    @Synchronized
    private fun onEvent(eventDir: File) {
        // Chapter dirs may have been added/removed — resync
        observeChapterDirs()

        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(debounceMs)
            Timber.d("MangaFileObserver: emitting change (triggered by ${eventDir.name})")
            _changes.emit(Unit)
        }
    }

    /**
     * Sync chapter-level observers with current children of rootDir.
     * Adds observers for new manga dirs, removes stale ones.
     */
    @Synchronized
    private fun observeChapterDirs() {
        val currentDirs = rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.map { it.absolutePath }
            ?.toSet()
            ?: emptySet()

        // Remove stale
        val stale = chapterObservers.keys - currentDirs
        stale.forEach { path ->
            chapterObservers.remove(path)?.stopWatching()
        }

        // Add new
        val newDirs = currentDirs - chapterObservers.keys
        newDirs.forEach { path ->
            val dir = File(path)
            val observer = createObserver(dir)
            observer.startWatching()
            chapterObservers[path] = observer
        }
    }

    private fun createObserver(dir: File): FileObserver {
        return object : FileObserver(dir.absolutePath, eventMask) {
            override fun onEvent(event: Int, path: String?) {
                onEvent(dir)
            }
        }
    }
}
