package com.mangareader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reading_progress")
data class ReadingProgress(
    @PrimaryKey val mangaDir: String,
    val mangaName: String,
    val chapterDir: String,
    val chapterName: String,
    val pageIndex: Int,
    val totalPages: Int,
    val lastReadAt: Long = System.currentTimeMillis(),
    val coverPath: String? = null,
)

@Entity(tableName = "manga_tags")
data class MangaTag(
    @PrimaryKey val mangaDir: String,
    val tag: String, // reading, completed, dropped, planned
)

@Entity(tableName = "bookmarks")
data class Bookmark(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val mangaDir: String,
    val mangaName: String,
    val chapterDir: String,
    val chapterName: String,
    val pageIndex: Int,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "cbz_cache")
data class MangaCache(
    @PrimaryKey val cbzPath: String,
    val extractedDir: String,
    val pageCount: Int,
    val extractedAt: Long = System.currentTimeMillis(),
)
