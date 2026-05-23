package com.ayrindigital.drme2edemo.downloads

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import androidx.media3.database.StandaloneDatabaseProvider
import com.ayrindigital.drme2edemo.R
import okhttp3.OkHttpClient
import java.io.File

class DemoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    0,
) {
    override fun getDownloadManager(): DownloadManager = downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val activeCount = downloads.count { it.state == Download.STATE_DOWNLOADING }
        val completedCount = downloads.count { it.state == Download.STATE_COMPLETED }
        val title = when {
            activeCount > 0 -> "Downloading ($activeCount)"
            completedCount > 0 -> "Downloaded ($completedCount)"
            else -> "Ready"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setOngoing(activeCount > 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_channel"

        private var serviceInstance: DemoDownloadService? = null

        private val downloadManager by lazy {
            val context = serviceInstance ?: error("Service not initialized")
            val databaseProvider = StandaloneDatabaseProvider(context)
            val cacheDir = File(context.getExternalFilesDir(null), "downloads")
            cacheDir.mkdirs()

            val downloadIndex = DefaultDownloadIndex(databaseProvider)
            val cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(100 * 1024 * 1024))

            val okHttpClient = OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            val cacheDataSourceFactory = CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpDataSourceFactory)

            DownloadManager(context, DefaultDownloadIndex(databaseProvider), DefaultDownloaderFactory(cacheDataSourceFactory))
        }

        fun startDownload(context: Context, contentId: String, manifestUrl: String) {
            val request = DownloadRequest.Builder(contentId, Uri.parse(manifestUrl)).build()
            sendAddDownload(context, DemoDownloadService::class.java, request, false)
        }

        fun pauseDownload(context: Context, id: String) {
            sendSetStopReason(
                context,
                DemoDownloadService::class.java,
                id,
                Download.STOP_REASON_NONE,
                /* foreground = */ false,
            )
        }

        fun removeDownload(context: Context, id: String) {
            sendRemoveDownload(context, DemoDownloadService::class.java, id, /* foreground = */ false)
        }
    }

    init {
        serviceInstance = this
    }
}
