package com.mangareader.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.util.concurrent.TimeUnit

@Serializable
data class MangaSearchResult(
    val id: Long,
    val slug_url: String = "",
    val name: String = "",
    val rus_name: String = "",
    val eng_name: String = "",
    val cover: Cover? = null,
    val chapters_count: Int = 0,
) {
    @Serializable
    data class Cover(val default: String = "", val thumbnail: String = "")

    val displayName: String get() = rus_name.ifBlank { name.ifBlank { eng_name } }
    val coverUrl: String get() = cover?.default?.let { "https://img3.mixlib.me$it" } ?: ""
    val coverThumbUrl: String get() = cover?.thumbnail?.let { "https://img3.mixlib.me$it" } ?: coverUrl
}

@Serializable
data class ChapterInfo(
    val number: String = "",
    val volume: String = "",
    val name: String? = null,
) {
    val numberFloat: Float get() = number.toFloatOrNull() ?: 0f
    val displayNumber: String get() {
        val f = numberFloat
        return if (f == f.toLong().toFloat()) f.toLong().toString() else f.toString()
    }
}

@Serializable
data class ChapterPagesResponse(
    val data: ChapterPagesData,
) {
    @Serializable
    data class ChapterPagesData(val pages: List<PageData>)

    @Serializable
    data class PageData(val slug: Int = 0, val url: String = "")
}

@Serializable
data class ChaptersListResponse(
    val data: List<ChapterInfo>,
)

@Serializable
data class MangaSearchResponse(
    val data: List<MangaSearchResult>,
)

@Serializable
data class MangaInfoResponse(
    val data: MangaSearchResult,
)

object MangalibApi {
    private const val API_BASE = "https://api.cdnlibs.org/api/manga"
    const val IMAGE_SERVER = "https://img3.mixlib.me"
    private const val REFERER = "https://mangalib.me/"
    private const val DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var cookies: String = ""

    fun setCookies(cookieMap: Map<String, String>) {
        cookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun baseRequest(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("User-Agent", DEFAULT_UA)
        .header("Accept", "application/json, text/plain, */*")
        .header("Referer", REFERER)
        .header("Origin", "https://mangalib.me")
        .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }

    private suspend fun getJson(url: String): String = withContext(Dispatchers.IO) {
        LogCollector.i("API", "GET $url")
        val request = baseRequest(url).build()
        try {
            val response = client.newCall(request).execute()
            LogCollector.d("API", "Response ${response.code} for $url (${response.body?.contentLength()} bytes)")
            if (!response.isSuccessful) {
                LogCollector.e("API", "HTTP ${response.code}: $url")
                throw RuntimeException("HTTP ${response.code}: $url")
            }
            val body = response.body?.string() ?: throw RuntimeException("Empty response: $url")
            LogCollector.d("API", "Body length: ${body.length} chars")
            if (body.length < 200) {
                LogCollector.w("API", "Suspiciously short body: $body")
            }
            body
        } catch (e: Exception) {
            LogCollector.e("API", "Request failed: $url", e)
            throw e
        }
    }

    /**
     * Search manga by name. Returns list of results from MangaLib API.
     */
    suspend fun searchManga(query: String): List<MangaSearchResult> = withContext(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = "$API_BASE?q=$encoded"
            LogCollector.i("Search", "Searching: '$query' → $url")
            val body = getJson(url)
            val response = json.decodeFromString<MangaSearchResponse>(body)
            LogCollector.i("Search", "Found ${response.data.size} results for '$query'")
            response.data.forEachIndexed { i, m ->
                LogCollector.d("Search", "  [$i] ${m.displayName} (${m.slug_url}) ${m.chapters_count} chapters")
            }
            response.data
        } catch (e: Exception) {
            LogCollector.e("Search", "searchManga failed for: '$query'", e)
            emptyList()
        }
    }

    /**
     * Get manga info by slug (e.g. "43165--ao-no-hako").
     */
    suspend fun getMangaInfo(slug: String): MangaSearchResult? = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/$slug"
            LogCollector.i("API", "getMangaInfo: $slug")
            val body = getJson(url)
            val response = json.decodeFromString<MangaInfoResponse>(body)
            LogCollector.i("API", "Manga info: ${response.data.displayName}")
            response.data
        } catch (e: Exception) {
            LogCollector.e("API", "getMangaInfo failed for: $slug", e)
            null
        }
    }

    /**
     * Get chapters list for a manga by slug.
     */
    suspend fun getChapters(slug: String): List<ChapterInfo> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/$slug/chapters"
            val body = getJson(url)
            val response = json.decodeFromString<ChaptersListResponse>(body)
            response.data.sortedBy { it.numberFloat }
        } catch (e: Exception) {
            Timber.e(e, "getChapters failed for: $slug")
            emptyList()
        }
    }

    /**
     * Get page URLs for a specific chapter.
     */
    suspend fun getChapterPages(slug: String, chapterNumber: String, volume: String): List<ChapterPagesResponse.PageData> = withContext(Dispatchers.IO) {
        try {
            val url = "$API_BASE/$slug/chapter?number=$chapterNumber&volume=$volume"
            val body = getJson(url)
            val response = json.decodeFromString<ChapterPagesResponse>(body)
            response.data.pages
        } catch (e: Exception) {
            Timber.e(e, "getChapterPages failed for: $slug ch$chapterNumber vol$volume")
            emptyList()
        }
    }

    /**
     * Build full image URL from page data.
     */
    fun buildImageUrl(pageUrl: String): String = "$IMAGE_SERVER$pageUrl"

    /**
     * Download an image and return bytes.
     */
    suspend fun downloadImage(imageUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", REFERER)
                .build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                response.body?.bytes()
            } else {
                Timber.e("downloadImage HTTP ${response.code}: $imageUrl")
                null
            }
        } catch (e: Exception) {
            Timber.e(e, "downloadImage failed: $imageUrl")
            null
        }
    }
}
