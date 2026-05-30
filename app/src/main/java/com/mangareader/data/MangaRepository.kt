package com.mangareader.data

import android.content.Context
import com.mangareader.data.db.Bookmark
import com.mangareader.data.db.MangaTag
import com.mangareader.data.db.ProgressDao
import com.mangareader.data.db.ReadingProgress
import com.mangareader.data.network.LogCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * Abstraction over manga file system access for testability.
 */
interface MangaDataSource {
    suspend fun getLibrary(root: File): List<Manga>
    fun getChapters(mangaDir: File): List<Chapter>
    suspend fun getPages(chapterPath: File, context: Context, dao: ProgressDao): List<Page>
    fun getPagesSync(chapterDir: File): List<Page>
}

class DefaultMangaDataSource : MangaDataSource {
    override suspend fun getLibrary(root: File): List<Manga> = MangaStore.scanManga(root)
    override fun getChapters(mangaDir: File): List<Chapter> = MangaStore.scanChapters(mangaDir)
    override suspend fun getPages(chapterPath: File, context: Context, dao: ProgressDao): List<Page> =
        MangaStore.scanPages(chapterPath, context, dao)
    override fun getPagesSync(chapterDir: File): List<Page> = MangaStore.scanPages(chapterDir)
}

/**
 * Repository combining manga file system access with DB persistence.
 * Centralises caching logic that was previously scattered in the ViewModel.
 */
class MangaRepository(
    private val context: Context,
    private val dao: ProgressDao,
    private val source: MangaDataSource = DefaultMangaDataSource(),
) {
    private var mangasCache: List<Manga>? = null
    private val chaptersCache = mutableMapOf<String, List<Chapter>>()

    // --- Library ---

    suspend fun getLibrary(root: File): List<Manga> = withContext(Dispatchers.IO) {
        mangasCache?.let {
            Timber.d("Repo: library cache HIT (%d mangas)", it.size)
            it
        } ?: source.getLibrary(root).also {
            Timber.i("Repo: library cache MISS → scanned %d mangas from %s", it.size, root.absolutePath)
            mangasCache = it
        }
    }

    fun invalidateCache() {
        Timber.i("Repo: cache invalidated (library + %d chapter entries)", chaptersCache.size)
        mangasCache = null
        chaptersCache.clear()
    }

    // --- Chapters ---

    suspend fun getChapters(mangaDir: String): List<Chapter> = withContext(Dispatchers.IO) {
        chaptersCache[mangaDir]?.let {
            Timber.d("Repo: chapters cache HIT for %s (%d chapters)", mangaDir, it.size)
            it
        } ?: source.getChapters(File(mangaDir)).also {
            Timber.d("Repo: chapters cache MISS for %s → %d chapters", mangaDir, it.size)
            chaptersCache[mangaDir] = it
        }
    }

    // --- Pages (delegates to MangaStore with CBZ support) ---

    suspend fun getPages(chapterDir: String): List<Page> = withContext(Dispatchers.IO) {
        Timber.d("Repo: getPages(%s)", chapterDir)
        val path = File(chapterDir)
        val pages = source.getPages(path, context, dao)
        Timber.d("Repo: getPages returned %d pages", pages.size)
        pages
    }

    // --- Progress ---

    fun getAllProgress(): Flow<List<ReadingProgress>> = dao.getAllProgress()

    suspend fun getProgress(mangaDir: String): ReadingProgress? = dao.getProgress(mangaDir)

    suspend fun saveProgress(
        mangaDir: String,
        mangaName: String,
        chapterDir: String,
        chapterName: String,
        pageIndex: Int,
        totalPages: Int,
        coverPath: String?,
    ) {
        dao.upsertProgress(
            ReadingProgress(
                mangaDir = mangaDir,
                mangaName = mangaName,
                chapterDir = chapterDir,
                chapterName = chapterName,
                pageIndex = pageIndex,
                totalPages = totalPages,
                coverPath = coverPath,
            )
        )
    }

    suspend fun upsertProgress(progress: ReadingProgress) = dao.upsertProgress(progress)

    // --- Tags ---

    fun getAllTags(): Flow<List<MangaTag>> = dao.getAllTags()

    suspend fun setTag(tag: MangaTag) = dao.setTag(tag)

    // --- Bookmarks ---

    fun getBookmarks(mangaDir: String): Flow<List<Bookmark>> = dao.getBookmarks(mangaDir)

    fun getRecentBookmarks(limit: Int = 50): Flow<List<Bookmark>> = dao.getRecentBookmarks(limit)

    suspend fun addBookmark(bookmark: Bookmark) = dao.addBookmark(bookmark)

    suspend fun deleteBookmark(id: Int) = dao.deleteBookmark(id)

    // --- History ---

    fun getRecentHistory(limit: Int = 20): Flow<List<ReadingProgress>> = dao.getRecentHistory(limit)

    // --- CBZ Cache ---

    suspend fun cleanStaleCbzCache() = MangaStore.cleanStaleCbzCache(context, dao)

    suspend fun clearAllCbzCache() = MangaStore.clearAllCbzCache(context, dao)
}
