package com.ayrindigital.drme2edemo.di

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DownloadModule {
    private const val DOWNLOAD_CACHE_DIR = "downloads"
    private const val CACHE_SIZE_BYTES = 500L * 1024 * 1024

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideDownloadCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
    ): SimpleCache {
        val cacheDir = File(context.getExternalFilesDir(null), DOWNLOAD_CACHE_DIR).apply { mkdirs() }
        return SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(CACHE_SIZE_BYTES), databaseProvider)
    }

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        downloadCache: SimpleCache,
        okHttpClient: OkHttpClient,
    ): DownloadManager {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        val downloaderFactory = DefaultDownloaderFactory(
            androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setCache(downloadCache)
                .setUpstreamDataSourceFactory(httpFactory),
            Executors.newFixedThreadPool(4),
        )
        return DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            downloaderFactory,
        )
    }
}
