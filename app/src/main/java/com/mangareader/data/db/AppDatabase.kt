package com.mangareader.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ReadingProgress::class, MangaTag::class, Bookmark::class, MangaCache::class],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        /** v2 → v3: added cbz_cache table */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS `cbz_cache` (
                        `cbzPath` TEXT NOT NULL,
                        `extractedDir` TEXT NOT NULL,
                        `pageCount` INTEGER NOT NULL,
                        `extractedAt` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`cbzPath`)
                    )"""
                )
            }
        }

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "manga_reader.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Safety net only for unknown v1→v2 (schema not available)
                    .fallbackToDestructiveMigrationFrom(1)
                    .build().also { INSTANCE = it }
            }
    }
}
