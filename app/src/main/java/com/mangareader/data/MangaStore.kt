package com.mangareader.data

import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
private const val CBZ_CACHE_DIR = "cbz_cache"

data class MangaMetadata(
    val title: String? = null,
    val author: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val status: String? = null,
)

@Immutable
data class Manga(
    val name: String,
    val dirPath: String,
    val coverPath: String?,
    val chapterCount: Int = 0,
    val metadata: MangaMetadata? = null,
) {
    val dir: File get() = File(dirPath)
    val coverUri: Uri? get() = coverPath?.let { Uri.fromFile(File(it)) }
    val displayName: String get() = metadata?.title ?: name
    val author: String? get() = metadata?.author
}

@Immutable
data class Chapter(
    val name: String,
    val dirPath: String,
    val number: Float = 0f,
) {
    val dir: File get() = File(dirPath)
}

@Immutable
data class Page(
    val filePath: String,
    val index: Int,
) {
    val file: File get() = File(filePath)
}

object MangaStore {

    fun isCbzFile(file: File): Boolean {
        return file.isFile && file.extension.equals("cbz", ignoreCase = true)
    }

    suspend fun scanManga(root: File): List<Manga> = withContext(Dispatchers.IO) {
        Timber.i("scanManga: scanning root=${root.absolutePath}")
        if (!root.isDirectory) return@withContext emptyList()
        val mangaDirs = root.listFiles()?.filter { it.isDirectory } ?: return@withContext emptyList()
        val result = mangaDirs.map { async { scanSingleManga(it) } }.awaitAll()
            .filterNotNull()
            .sortedBy { it.name.lowercase() }
        Timber.i("scanManga: found ${result.size} manga in ${root.absolutePath}")
        result
    }

    fun parseMangaJson(mangaDir: File): MangaMetadata? {
        val jsonFile = mangaDir.listFiles()?.firstOrNull {
            it.isFile && (it.name.equals("manga.json", ignoreCase = true) ||
                           it.name.equals("info.json", ignoreCase = true) ||
                           it.name.equals("meta.json", ignoreCase = true))
        } ?: return null
        return try {
            val json = JSONObject(jsonFile.readText())
            MangaMetadata(
                title = json.optString("title", null),
                author = json.optString("author", null),
                description = json.optString("description", null),
                tags = json.optJSONArray("tags")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optString(it) }
                } ?: emptyList(),
                status = json.optString("status", null),
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse metadata from ${jsonFile.absolutePath}")
            null
        }
    }

    private fun scanSingleManga(mangaDir: File): Manga? {
        val chapterDirs = mangaDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("chapter_") }
        val cbzFiles = mangaDir.listFiles()
            ?.filter { isCbzFile(it) && it.nameWithoutExtension.startsWith("chapter_") }
        if (chapterDirs.isNullOrEmpty() && cbzFiles.isNullOrEmpty()) return null
        val chapterCount = (chapterDirs?.size ?: 0) + (cbzFiles?.size ?: 0)
        val cover = chapterDirs?.minByOrNull { it.name }?.let { chDir ->
            chDir.listFiles()?.firstOrNull { f ->
                f.isFile && f.extension.lowercase() in IMAGE_EXTENSIONS
            }
        }
        val metadata = parseMangaJson(mangaDir)
        return Manga(
            name = metadata?.title ?: mangaDir.name.replace("-", " ").replace("_", " "),
            dirPath = mangaDir.absolutePath,
            coverPath = cover?.absolutePath,
            chapterCount = chapterCount,
            metadata = metadata,
        )
    }

    fun scanChapters(mangaDir: File): List<Chapter> {
        Timber.d("scanChapters: mangaDir=${mangaDir.absolutePath}")
        if (!mangaDir.isDirectory) return emptyList()

        val dirChapters = mangaDir.listFiles()
            ?.filter { it.isDirectory && it.name.startsWith("chapter_") }
            ?.map { chDir ->
                val numStr = chDir.name.removePrefix("chapter_")
                Chapter(
                    name = numStr,
                    dirPath = chDir.absolutePath,
                    number = numStr.toFloatOrNull() ?: 0f,
                )
            } ?: emptyList()

        val cbzChapters = mangaDir.listFiles()
            ?.filter { isCbzFile(it) }
            ?.map { cbzFile ->
                val numStr = cbzFile.nameWithoutExtension.removePrefix("chapter_")
                Chapter(
                    name = numStr,
                    dirPath = cbzFile.absolutePath,
                    number = numStr.toFloatOrNull() ?: 0f,
                )
            } ?: emptyList()

        val result = (dirChapters + cbzChapters).sortedBy { it.number }
        Timber.d("scanChapters: found ${result.size} chapters in ${mangaDir.absolutePath}")
        return result
    }

    /**
     * Scan pages from a chapter path. Handles directory-based and CBZ-based chapters.
     * For CBZ: uses DB-tracked cache for lazy extraction on first access.
     *
     * @param chapterPath path to chapter directory or .cbz file
     * @param context Android context for cache directory access
     * @param dao ProgressDao for CBZ cache tracking in DB
     */
    suspend fun scanPages(
        chapterPath: File,
        context: android.content.Context,
        dao: com.mangareader.data.db.ProgressDao,
    ): List<Page> = withContext(Dispatchers.IO) {
        Timber.d("scanPages: chapterPath=${chapterPath.absolutePath}")
        val result = when {
            chapterPath.isDirectory -> scanDirectoryPages(chapterPath)
            isCbzFile(chapterPath) -> extractAndScanCbz(chapterPath, context, dao)
            else -> emptyList()
        }
        Timber.d("scanPages: found ${result.size} pages in ${chapterPath.absolutePath}")
        result
    }

    /**
     * Synchronous version for directory-based chapters (backward compatibility).
     * Does NOT handle CBZ — use the suspend variant with context+dao for CBZ chapters.
     */
    fun scanPages(chapterDir: File): List<Page> {
        if (!chapterDir.isDirectory) return emptyList()
        return scanDirectoryPages(chapterDir)
    }

    private fun scanDirectoryPages(dir: File): List<Page> {
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
            ?.sortedBy { it.name }
            ?.mapIndexed { index, file -> Page(filePath = file.absolutePath, index = index) }
            ?: emptyList()
    }

    /**
     * Extract a CBZ file to cache directory and return pages.
     * Uses database cache tracking to avoid re-extraction.
     */
    private suspend fun extractAndScanCbz(
        cbzFile: File,
        context: android.content.Context,
        dao: com.mangareader.data.db.ProgressDao,
    ): List<Page> = withContext(Dispatchers.IO) {
        val cbzPath = cbzFile.absolutePath

        // Check DB cache — if extraction dir exists and has files, skip re-extraction
        val cached = dao.getCbzCache(cbzPath)
        if (cached != null) {
            val cacheDir = File(cached.extractedDir)
            if (cacheDir.isDirectory && cacheDir.listFiles()?.isNotEmpty() == true) {
                Timber.d("extractAndScanCbz: cache hit for ${cbzFile.name}")
                return@withContext scanDirectoryPages(cacheDir)
            }
            Timber.d("extractAndScanCbz: cache miss (dir missing/empty) for ${cbzFile.name}")
        } else {
            Timber.d("extractAndScanCbz: no cache entry for ${cbzFile.name}")
        }

        // Compute extraction target in app's cache dir
        val cacheRoot = File(context.cacheDir, CBZ_CACHE_DIR)
        val extractDir = cacheRoot.resolve(cbzFile.nameWithoutExtension)

        try {
            extractCbzArchive(cbzFile, extractDir)
            val pages = scanDirectoryPages(extractDir)
            Timber.i("extractAndScanCbz: extracted ${pages.size} pages from ${cbzFile.name}")

            // Persist to DB
            dao.upsertCbzCache(
                com.mangareader.data.db.MangaCache(
                    cbzPath = cbzPath,
                    extractedDir = extractDir.absolutePath,
                    pageCount = pages.size,
                )
            )

            pages
        } catch (e: Exception) {
            Timber.e(e, "Failed to extract CBZ: ${cbzFile.name}")
            extractDir.deleteRecursively()
            emptyList()
        }
    }

    /**
     * Extract all images from a CBZ archive into the target directory.
     * Handles flat and nested ZIP structures by flattening to filenames.
     * Guards against zip-slip attacks.
     * Throws IOException on corrupt or password-protected archives.
     */
    private fun extractCbzArchive(cbzFile: File, targetDir: File) {
        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetDir.mkdirs()

        var extractedCount = 0

        cbzFile.inputStream().buffered().use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val fileName = entry.name.substringAfterLast('/')
                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        if (fileName.isNotEmpty() && ext in IMAGE_EXTENSIONS) {
                            val outFile = File(targetDir, fileName)
                            // zip-slip guard
                            if (!outFile.canonicalPath.startsWith(targetDir.canonicalPath + File.separator) &&
                                outFile.canonicalPath != targetDir.canonicalPath
                            ) {
                                Timber.w("Zip-slip detected, skipping: ${entry.name}")
                                zis.closeEntry()
                                entry = zis.nextEntry
                                continue
                            }
                            extractFile(zis, outFile)
                            extractedCount++
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        if (extractedCount == 0) {
            throw IOException("CBZ archive contains no images: ${cbzFile.name}")
        }
    }

    private fun extractFile(zis: ZipInputStream, outFile: File) {
        outFile.parentFile?.mkdirs()
        BufferedOutputStream(FileOutputStream(outFile)).use { bos ->
            val buffer = ByteArray(8192)
            var len: Int
            while (zis.read(buffer).also { len = it } > 0) {
                bos.write(buffer, 0, len)
            }
        }
    }

    /**
     * Remove stale CBZ cache entries: source CBZ deleted or cache dir cleared by OS.
     */
    suspend fun cleanStaleCbzCache(
        context: android.content.Context,
        dao: com.mangareader.data.db.ProgressDao,
    ) = withContext(Dispatchers.IO) {
        val allCaches = dao.getAllCbzCaches()
        Timber.d("cleanStaleCbzCache: checking ${allCaches.size} cache entries")
        var staleCount = 0
        for (cache in allCaches) {
            val sourceExists = File(cache.cbzPath).exists()
            val cacheExists = File(cache.extractedDir).let {
                it.isDirectory && it.listFiles()?.isNotEmpty() == true
            }
            if (!sourceExists || !cacheExists) {
                File(cache.extractedDir).deleteRecursively()
                staleCount++
            }
        }
        if (staleCount > 0) Timber.d("cleanStaleCbzCache: removed $staleCount stale entries")
        // Clean orphan dirs not tracked in DB
        val cbzCacheRoot = File(context.cacheDir, CBZ_CACHE_DIR)
        if (cbzCacheRoot.isDirectory) {
            val trackedDirs = allCaches.map { File(it.extractedDir).name }.toSet()
            cbzCacheRoot.listFiles()?.forEach { dir ->
                if (dir.isDirectory && dir.name !in trackedDirs) {
                    dir.deleteRecursively()
                }
            }
        }
    }

    /**
     * Full reset: delete all CBZ caches and DB tracking.
     */
    suspend fun clearAllCbzCache(
        context: android.content.Context,
        dao: com.mangareader.data.db.ProgressDao,
    ) = withContext(Dispatchers.IO) {
        val cbzCacheRoot = File(context.cacheDir, CBZ_CACHE_DIR)
        if (cbzCacheRoot.isDirectory) {
            cbzCacheRoot.deleteRecursively()
        }
        dao.clearAllCbzCache()
    }
}
