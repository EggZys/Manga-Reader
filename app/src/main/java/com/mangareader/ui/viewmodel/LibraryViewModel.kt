package com.mangareader.ui.viewmodel

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangareader.data.Chapter
import com.mangareader.data.Manga
import com.mangareader.data.MangaFileObserver
import com.mangareader.data.MangaRepository
import com.mangareader.data.Page
import com.mangareader.data.ReadingMode
import com.mangareader.data.ThemeMode
import com.mangareader.data.ThemePreferences
import com.mangareader.data.db.AppDatabase
import com.mangareader.data.db.ReadingProgress
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

data class MangaWithProgress(
    val manga: Manga,
    val progress: ReadingProgress?,
    val tag: String?,
    val chaptersRead: Int = 0,
    val totalChapters: Int = 0,
)

data class ContinueReading(
    val manga: MangaWithProgress,
    val chapterIndex: Int,
    val pageIndex: Int,
)

sealed class LibraryState {
    data object Loading : LibraryState()
    data class Ready(
        val mangas: List<MangaWithProgress>,
        val continueReading: ContinueReading?,
        val sortBy: SortBy,
        val searchQuery: String = "",
        val filterTag: String? = null,
        val availableTags: List<String> = listOf("reading", "completed", "dropped", "planned"),
    ) : LibraryState()
}

enum class SortBy { NAME, LAST_READ, PROGRESS }

@Suppress("DEPRECATION")
class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MangaRepository(
        context = app,
        dao = AppDatabase.get(app).progressDao(),
    )

    private val defaultRootDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "manga"
    )

    private var rootDir: File = defaultRootDir

    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _progressMap = MutableStateFlow<Map<String, ReadingProgress>>(emptyMap())
    private val _tagMap = MutableStateFlow<Map<String, String>>(emptyMap())
    private val _sortBy = MutableStateFlow(SortBy.NAME)
    private val _searchQuery = MutableStateFlow("")
    private val _filterTag = MutableStateFlow<String?>(null)

    private var refreshJob: Job? = null
    private val fileObserver = MangaFileObserver(rootDir)

    val themeMode: Flow<ThemeMode> = ThemePreferences.themeModeFlow(app)
    val dynamicColor: Flow<Boolean> = ThemePreferences.dynamicColorFlow(app)
    val readingMode: Flow<ReadingMode> = ThemePreferences.readingModeFlow(app)
    val mangaFolder: Flow<String> = ThemePreferences.mangaFolderFlow(app)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { ThemePreferences.setThemeMode(getApplication(), mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { ThemePreferences.setDynamicColor(getApplication(), enabled) }
    }

    fun setReadingMode(mode: ReadingMode) {
        viewModelScope.launch { ThemePreferences.setReadingMode(getApplication(), mode) }
    }

    fun setMangaFolder(path: String) {
        viewModelScope.launch { ThemePreferences.setMangaFolder(getApplication(), path) }
    }

    init {
        Timber.i("LibraryVM: init, rootDir=%s", rootDir.absolutePath)
        viewModelScope.launch {
            forceRefresh()
            launch {
                repo.getAllProgress().collect { list ->
                    _progressMap.value = list.associateBy { it.mangaDir }
                    debouncedRefresh()
                }
            }
            launch {
                repo.getAllTags().collect { list ->
                    _tagMap.value = list.associate { it.mangaDir to it.tag }
                    debouncedRefresh()
                }
            }
            launch {
                fileObserver.changes.collect { forceRefresh() }
            }
            // Clean stale CBZ caches on startup
            launch { repo.cleanStaleCbzCache() }
            // React to manga folder changes from settings
            launch {
                ThemePreferences.mangaFolderFlow(getApplication()).collect { path ->
                    val newDir = if (path.isBlank()) defaultRootDir else File(path)
                    if (newDir.absolutePath != rootDir.absolutePath) {
                        rootDir = newDir
                        fileObserver.stop()
                        fileObserver.updateRoot(newDir)
                        fileObserver.start()
                        forceRefresh()
                    }
                }
            }
        }
        fileObserver.start()
    }

    override fun onCleared() {
        super.onCleared()
        fileObserver.stop()
    }

    private fun debouncedRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(300)
            refresh()
        }
    }

    fun forceRefresh() {
        Timber.i("LibraryVM: forceRefresh()")
        viewModelScope.launch {
            repo.invalidateCache()
            refresh()
        }
    }

    private suspend fun refresh() {
        Timber.d("LibraryVM: refresh() started, rootDir=%s", rootDir.absolutePath)
        val mangas = repo.getLibrary(rootDir)
        val progs = _progressMap.value
        val tags = _tagMap.value
        val sortBy = _sortBy.value

        val mangaWithProgress = mangas.map { m ->
            val prog = progs[m.dirPath]
            val tag = tags[m.dirPath]
            val chapters = repo.getChapters(m.dirPath)
            val chaptersRead = if (prog != null) {
                val chIdx = chapters.indexOfFirst { it.dirPath == prog.chapterDir }
                if (chIdx >= 0) chIdx + 1 else 0
            } else 0

            MangaWithProgress(
                manga = m,
                progress = prog,
                tag = tag,
                chaptersRead = chaptersRead,
                totalChapters = chapters.size,
            )
        }

        val sorted = when (sortBy) {
            SortBy.NAME -> mangaWithProgress.sortedBy { it.manga.name.lowercase() }
            SortBy.LAST_READ -> mangaWithProgress.sortedByDescending { it.progress?.lastReadAt ?: 0 }
            SortBy.PROGRESS -> mangaWithProgress.sortedByDescending {
                if (it.totalChapters > 0) it.chaptersRead.toFloat() / it.totalChapters else 0f
            }
        }

        val filtered = sorted
            .let { list ->
                val query = _searchQuery.value.trim()
                if (query.isBlank()) list
                else list.filter { it.manga.name.contains(query, ignoreCase = true) }
            }
            .let { list ->
                val tag = _filterTag.value
                if (tag == null) list
                else list.filter { it.tag == tag }
            }

        val continueReading = filtered.firstOrNull { it.progress != null }?.let { m ->
            val chapters = repo.getChapters(m.manga.dirPath)
            val chIdx = chapters.indexOfFirst { it.dirPath == m.progress?.chapterDir }
            if (chIdx >= 0) ContinueReading(m, chIdx, m.progress?.pageIndex ?: 0) else null
        }

        Timber.i("LibraryVM: refresh() done — %d mangas, %d filtered, sortBy=%s, query='%s', filterTag=%s",
            mangas.size, filtered.size, sortBy, _searchQuery.value, _filterTag.value)

        _state.value = LibraryState.Ready(
            mangas = filtered,
            continueReading = continueReading,
            sortBy = sortBy,
            searchQuery = _searchQuery.value,
            filterTag = _filterTag.value,
        )
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        debouncedRefresh()
    }

    fun setFilterTag(tag: String?) {
        _filterTag.value = tag
        debouncedRefresh()
    }

    fun setSortBy(sort: SortBy) {
        _sortBy.value = sort
        debouncedRefresh()
    }

    fun setTag(mangaDir: String, tag: String) {
        viewModelScope.launch { repo.setTag(com.mangareader.data.db.MangaTag(mangaDir, tag)) }
    }

    fun saveProgress(mangaDir: String, mangaName: String, chapterDir: String,
                     chapterName: String, pageIndex: Int, totalPages: Int, coverPath: String?) {
        viewModelScope.launch {
            repo.saveProgress(mangaDir, mangaName, chapterDir, chapterName, pageIndex, totalPages, coverPath)
        }
    }

    fun getBookmarks(mangaDir: String) = repo.getBookmarks(mangaDir)

    fun getRecentHistory() = repo.getRecentHistory()

    fun addBookmark(mangaDir: String, mangaName: String, chapterDir: String,
                    chapterName: String, pageIndex: Int) {
        viewModelScope.launch {
            repo.addBookmark(com.mangareader.data.db.Bookmark(0, mangaDir, mangaName, chapterDir, chapterName, pageIndex))
        }
    }

    fun deleteBookmark(id: Int) {
        viewModelScope.launch { repo.deleteBookmark(id) }
    }

    suspend fun getChapters(mangaDir: String): List<Chapter> = repo.getChapters(mangaDir)

    suspend fun getPages(chapterDir: String): List<Page> = repo.getPages(chapterDir)

    suspend fun getProgress(mangaDir: String): ReadingProgress? = repo.getProgress(mangaDir)

    suspend fun getMangaDetails(mangaDir: String): MangaWithProgress? {
        val mangas = repo.getLibrary(rootDir)
        val manga = mangas.find { it.dirPath == mangaDir } ?: return null
        val prog = _progressMap.value[mangaDir]
        val tag = _tagMap.value[mangaDir]
        val chapters = repo.getChapters(manga.dirPath)
        val chaptersRead = if (prog != null) {
            val chIdx = chapters.indexOfFirst { it.dirPath == prog.chapterDir }
            if (chIdx >= 0) chIdx + 1 else 0
        } else 0

        return MangaWithProgress(
            manga = manga,
            progress = prog,
            tag = tag,
            chaptersRead = chaptersRead,
            totalChapters = chapters.size,
        )
    }

    fun getBookmarksForManga(mangaDir: String) = repo.getBookmarks(mangaDir)

    fun getRecentBookmarks() = repo.getRecentBookmarks()
}
