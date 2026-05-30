package com.mangareader.data.network

import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class DownloadProgress(
    val chapterNumber: String,
    val totalPages: Int,
    val downloadedPages: Int,
    val status: ChapterDownloadStatus,
)

enum class ChapterDownloadStatus { QUEUED, DOWNLOADING, DONE, ERROR, SKIPPED }

class DownloadManager(
    private val mangaDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        "manga"
    ),
    private val concurrency: Int = 4,
) {
    /**
     * Download selected chapters for a manga.
     * @param slug Manga slug (e.g. "43165--ao-no-hako")
     * @param mangaFolderName Folder name to save under (e.g. slug suffix or Russian name)
     * @param chapters Chapters to download
     * @param onProgress Called on each page/chapter progress update
     */
    suspend fun downloadChapters(
        slug: String,
        mangaFolderName: String,
        chapters: List<ChapterInfo>,
        onProgress: (DownloadProgress) -> Unit,
    ) = coroutineScope {
        Timber.i("downloadChapters: slug=$slug, chapters=${chapters.size}, outputDir=${mangaDir.absolutePath}/$mangaFolderName")
        val outDir = File(mangaDir, mangaFolderName)
        outDir.mkdirs()

        val semaphore = java.util.concurrent.Semaphore(concurrency)
        val jobs = chapters.map { ch ->
            launch(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    downloadSingleChapter(slug, ch, outDir, onProgress)
                } finally {
                    semaphore.release()
                }
            }
        }
        jobs.forEach { it.join() }
        Timber.i("downloadChapters: all chapters completed for slug=$slug")
    }

    private suspend fun downloadSingleChapter(
        slug: String,
        chapter: ChapterInfo,
        outDir: File,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        Timber.i("downloadSingleChapter: chapter=${chapter.displayNumber}")
        val chDir = File(outDir, "chapter_${chapter.displayNumber}")
        chDir.mkdirs()

        onProgress(DownloadProgress(chapter.displayNumber, 0, 0, ChapterDownloadStatus.DOWNLOADING))

        try {
            val pages = MangalibApi.getChapterPages(slug, chapter.number, chapter.volume)
            if (pages.isEmpty()) {
                onProgress(DownloadProgress(chapter.displayNumber, 0, 0, ChapterDownloadStatus.ERROR))
                return
            }

            val totalPages = pages.size
            var downloaded = 0
            var skippedExisting = 0

            for ((index, page) in pages.withIndex()) {
                val ext = page.url.substringAfterLast(".").ifBlank { "png" }
                val file = File(chDir, "page_${page.slug.toString().padStart(2, '0')}.$ext")

                // Skip if already exists and is valid
                if (file.exists() && file.length() > 1000) {
                    downloaded++
                    skippedExisting++
                    onProgress(DownloadProgress(chapter.displayNumber, totalPages, downloaded, ChapterDownloadStatus.DOWNLOADING))
                    continue
                }

                var success = false
                for (attempt in 0 until 3) {
                    try {
                        val imageUrl = MangalibApi.buildImageUrl(page.url)
                        val bytes = MangalibApi.downloadImage(imageUrl)
                        if (bytes != null && bytes.size > 1000) {
                            file.writeBytes(bytes)
                            downloaded++
                            success = true
                            break
                        }
                    } catch (e: Exception) {
                        Timber.w("Download page retry $attempt: ${e.message}")
                    }
                    delay(1000L * (attempt + 1))
                }

                if (!success) {
                    Timber.e("Failed to download page ${page.slug} of chapter ${chapter.displayNumber}")
                }

                onProgress(DownloadProgress(chapter.displayNumber, totalPages, downloaded, ChapterDownloadStatus.DOWNLOADING))
                delay(50) // Rate limiting
            }

            if (skippedExisting > 0) {
                Timber.d("Chapter ${chapter.displayNumber}: skipped $skippedExisting existing pages")
            }

            val status = if (downloaded >= totalPages) ChapterDownloadStatus.DONE
                         else if (downloaded > 0) ChapterDownloadStatus.DONE
                         else ChapterDownloadStatus.ERROR
            Timber.i("Chapter ${chapter.displayNumber}: downloaded $downloaded/$totalPages pages, status=$status")
            onProgress(DownloadProgress(chapter.displayNumber, totalPages, downloaded, status))
        } catch (e: Exception) {
            Timber.e(e, "downloadSingleChapter failed: ${chapter.displayNumber}")
            onProgress(DownloadProgress(chapter.displayNumber, 0, 0, ChapterDownloadStatus.ERROR))
        }
    }
}
