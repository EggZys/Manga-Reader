package com.mangareader.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: ReadingProgress)

    @Query("SELECT * FROM reading_progress ORDER BY lastReadAt DESC")
    fun getAllProgress(): Flow<List<ReadingProgress>>

    @Query("SELECT * FROM reading_progress WHERE mangaDir = :mangaDir LIMIT 1")
    suspend fun getProgress(mangaDir: String): ReadingProgress?

    @Query("DELETE FROM reading_progress WHERE mangaDir = :mangaDir")
    suspend fun deleteProgress(mangaDir: String)

    // Tags
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setTag(tag: MangaTag)

    @Query("SELECT * FROM manga_tags")
    fun getAllTags(): Flow<List<MangaTag>>

    @Query("SELECT tag FROM manga_tags WHERE mangaDir = :mangaDir LIMIT 1")
    suspend fun getTag(mangaDir: String): String?

    @Query("DELETE FROM manga_tags WHERE mangaDir = :mangaDir")
    suspend fun deleteTag(mangaDir: String)

    // Bookmarks
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addBookmark(bookmark: Bookmark)

    @Query("SELECT * FROM bookmarks WHERE mangaDir = :mangaDir ORDER BY createdAt DESC")
    fun getBookmarks(mangaDir: String): Flow<List<Bookmark>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentBookmarks(limit: Int = 50): Flow<List<Bookmark>>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmark(id: Int)

    // History (recently read chapters)
    @Query("SELECT * FROM reading_progress ORDER BY lastReadAt DESC LIMIT :limit")
    fun getRecentHistory(limit: Int = 20): Flow<List<ReadingProgress>>

    // CBZ cache
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCbzCache(cache: MangaCache)

    @Query("SELECT * FROM cbz_cache WHERE cbzPath = :cbzPath LIMIT 1")
    suspend fun getCbzCache(cbzPath: String): MangaCache?

    @Query("DELETE FROM cbz_cache")
    suspend fun clearAllCbzCache()

    @Query("SELECT * FROM cbz_cache")
    suspend fun getAllCbzCaches(): List<MangaCache>
}
