package com.ayrindigital.drme2edemo.downloads

import android.app.Notification
import android.content.Context
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.ayrindigital.drme2edemo.R

class DemoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.app_name,
    0,
) {
    override fun getDownloadManager(): DownloadManager {
        // TODO: Implement download manager
        return downloadManager
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        // TODO: Build proper notification
        val builder = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading")
            .setOngoing(true)
        return builder.build()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "download_channel"
        private lateinit var downloadManager: DownloadManager

        fun startDownload(
            context: Context,
            request: DownloadRequest,
        ) {
            sendAddDownload(
                context,
                DemoDownloadService::class.java,
                request,
                /* foreground = */ false,
            )
        }
    }
}
