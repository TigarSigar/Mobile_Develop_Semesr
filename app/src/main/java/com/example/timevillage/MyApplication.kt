package com.example.timevillage

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.util.DebugLogger

class MyApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // Используем 25% оперативы под картинки
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05) // 5% памяти телефона под кэш на диске
                    .build()
            }
            .logger(DebugLogger()) // Это покажет в Logcat: откуда взята картинка (Disk/Network)
            .respectCacheHeaders(false) // Принудительно кэшировать, даже если сервер молчит
            .build()
    }
}