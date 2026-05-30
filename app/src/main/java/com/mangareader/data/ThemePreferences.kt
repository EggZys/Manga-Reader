package com.mangareader.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class ReadingMode { PAGES, WEBTOON }

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreferences {

    private val THEME_KEY = stringPreferencesKey("theme")
    private val DYNAMIC_COLOR_KEY = booleanPreferencesKey("dynamic_color")
    private val READING_MODE_KEY = stringPreferencesKey("reading_mode")
    private val MANGA_FOLDER_KEY = stringPreferencesKey("manga_folder")

    // Theme
    fun themeModeFlow(context: Context): Flow<ThemeMode> =
        context.dataStore.data.map { prefs ->
            when (prefs[THEME_KEY]) {
                "light" -> ThemeMode.LIGHT
                "dark" -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
        }

    suspend fun setThemeMode(context: Context, mode: ThemeMode) {
        Timber.i("Theme mode changed to: $mode")
        context.dataStore.edit { it[THEME_KEY] = mode.name.lowercase() }
    }

    // Dynamic color
    fun dynamicColorFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[DYNAMIC_COLOR_KEY] ?: true
        }

    suspend fun setDynamicColor(context: Context, enabled: Boolean) {
        Timber.i("Dynamic color changed to: $enabled")
        context.dataStore.edit { it[DYNAMIC_COLOR_KEY] = enabled }
    }

    // Reading mode
    fun readingModeFlow(context: Context): Flow<ReadingMode> =
        context.dataStore.data.map { prefs ->
            when (prefs[READING_MODE_KEY]) {
                "webtoon" -> ReadingMode.WEBTOON
                else -> ReadingMode.PAGES
            }
        }

    suspend fun setReadingMode(context: Context, mode: ReadingMode) {
        Timber.i("Reading mode changed to: $mode")
        context.dataStore.edit { it[READING_MODE_KEY] = mode.name.lowercase() }
    }

    // Manga folder
    fun mangaFolderFlow(context: Context): Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[MANGA_FOLDER_KEY] ?: ""
        }

    suspend fun setMangaFolder(context: Context, path: String) {
        Timber.i("Manga folder changed to: $path")
        context.dataStore.edit { it[MANGA_FOLDER_KEY] = path }
    }
}
