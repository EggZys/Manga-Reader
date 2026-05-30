package com.mangareader.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangareader.data.network.ChapterInfo
import com.mangareader.data.network.ChapterDownloadStatus
import com.mangareader.data.network.DownloadManager
import com.mangareader.data.network.DownloadProgress
import com.mangareader.data.network.MangalibApi
import com.mangareader.data.network.MangaSearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

data class SearchState(
    val query: String = "",
    val results: List<MangaSearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
)

data class DownloadScreenState(
    val manga: MangaSearchResult? = null,
    val chapters: List<ChapterInfo> = emptyList(),
    val selectedChapters: Set<Float> = emptySet(),
    val isLoadingChapters: Boolean = false,
    val downloadProgress: Map<String, DownloadProgress> = emptyMap(),
    val isDownloading: Boolean = false,
    val downloadComplete: Boolean = false,
)

class DownloadViewModel(app: Application) : AndroidViewModel(app) {

    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    private val _downloadState = MutableStateFlow(DownloadScreenState())
    val downloadState: StateFlow<DownloadScreenState> = _downloadState.asStateFlow()

    private val downloadManager = DownloadManager()

    fun updateSearchQuery(query: String) {
        _searchState.value = _searchState.value.copy(query = query)
    }

    fun searchManga() {
        val query = _searchState.value.query.trim()
        if (query.isBlank()) return
        Timber.i("searchManga: query=\"$query\"")

        viewModelScope.launch {
            _searchState.value = _searchState.value.copy(isSearching = true, error = null)
            try {
                val results = MangalibApi.searchManga(query)
                Timber.i("searchManga: found ${results.size} results for \"$query\"")
                _searchState.value = _searchState.value.copy(
                    results = results,
                    isSearching = false,
                )
            } catch (e: Exception) {
                Timber.e(e, "Search failed")
                _searchState.value = _searchState.value.copy(
                    isSearching = false,
                    error = "Ошибка поиска: ${e.message}",
                )
            }
        }
    }

    fun selectManga(manga: MangaSearchResult) {
        Timber.i("selectManga: ${manga.name} (${manga.slug_url})")
        _downloadState.value = DownloadScreenState(manga = manga, isLoadingChapters = true)

        viewModelScope.launch {
            try {
                val chapters = MangalibApi.getChapters(manga.slug_url)
                _downloadState.value = _downloadState.value.copy(
                    chapters = chapters,
                    isLoadingChapters = false,
                )
            } catch (e: Exception) {
                Timber.e(e, "Load chapters failed")
                _downloadState.value = _downloadState.value.copy(isLoadingChapters = false)
            }
        }
    }

    fun toggleChapter(chapterNumber: Float) {
        val current = _downloadState.value.selectedChapters.toMutableSet()
        val action = if (current.contains(chapterNumber)) {
            current.remove(chapterNumber)
            "deselected"
        } else {
            current.add(chapterNumber)
            "selected"
        }
        Timber.d("toggleChapter: chapter $chapterNumber $action")
        _downloadState.value = _downloadState.value.copy(selectedChapters = current)
    }

    fun selectAllChapters() {
        Timber.d("selectAllChapters: selecting all ${_downloadState.value.chapters.size} chapters")
        _downloadState.value = _downloadState.value.copy(
            selectedChapters = _downloadState.value.chapters.map { it.numberFloat }.toSet(),
        )
    }

    fun selectNoneChapters() {
        Timber.d("selectNoneChapters: deselecting all chapters")
        _downloadState.value = _downloadState.value.copy(selectedChapters = emptySet())
    }

    fun selectRange(from: Float, to: Float) {
        val selected = _downloadState.value.chapters
            .filter { it.numberFloat in from..to }
            .map { it.numberFloat }
            .toSet()
        _downloadState.value = _downloadState.value.copy(selectedChapters = selected)
    }

    fun startDownload() {
        val state = _downloadState.value
        val manga = state.manga ?: return
        val selected = state.chapters.filter { state.selectedChapters.contains(it.numberFloat) }
        if (selected.isEmpty()) return

        val folderName = manga.slug_url.substringAfterLast("--").ifBlank { manga.slug_url }
        Timber.i("startDownload: slug=${manga.slug_url}, chapters=${selected.size}, folder=$folderName")

        _downloadState.value = state.copy(
            isDownloading = true,
            downloadComplete = false,
            downloadProgress = selected.associate {
                it.displayNumber to DownloadProgress(it.displayNumber, 0, 0, ChapterDownloadStatus.QUEUED)
            },
        )

        viewModelScope.launch {
            try {
                downloadManager.downloadChapters(
                    slug = manga.slug_url,
                    mangaFolderName = folderName,
                    chapters = selected,
                    onProgress = { progress ->
                        val current = _downloadState.value.downloadProgress.toMutableMap()
                        current[progress.chapterNumber] = progress
                        _downloadState.value = _downloadState.value.copy(downloadProgress = current)
                    },
                )
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    downloadComplete = true,
                )
                Timber.i("Download completed successfully")
            } catch (e: Exception) {
                Timber.e(e, "Download failed")
                _downloadState.value = _downloadState.value.copy(
                    isDownloading = false,
                    downloadComplete = true,
                )
            }
        }
    }

    fun resetDownloadState() {
        Timber.d("resetDownloadState: clearing download state")
        _downloadState.value = DownloadScreenState()
    }

    fun resetSearchState() {
        Timber.d("resetSearchState: clearing search state")
        _searchState.value = SearchState()
    }
}
