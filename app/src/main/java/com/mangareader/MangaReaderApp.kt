package com.mangareader

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mangareader.data.network.LogCollector
import timber.log.Timber

class MangaReaderApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Always plant CollectingTree (feeds into LogCollector for the Logs screen)
        Timber.plant(LogCollector.CollectingTree())
        LogCollector.init(this)
        LogCollector.i("App", "MangaReaderApp initialized")

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("manga_image_cache"))
                    .maxSizeBytes(200L * 1024 * 1024) // 200MB
                    .build()
            }
            .respectCacheHeaders(false)
            .build()
    }
}
