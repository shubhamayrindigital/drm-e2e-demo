package com.ayrindigital.drme2edemo.downloads

import android.app.Notification
import android.content.Context
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.ayrindigital.drme2edemo.MyApplication
import com.ayrindigital.drme2edemo.R
import com.ayrindigital.drme2edemo.data.downloads.DownloadRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class DemoDownloadService : DownloadService(
    FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    MyApplication.DOWNLOAD_CHANNEL_ID,
    R.string.app_name,
    0,
) {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DownloadEntryPoint {
        fun downloadManager(): DownloadManager
        fun downloadRepository(): DownloadRepository
    }

    override fun getDownloadManager(): DownloadManager {
        val entry = EntryPointAccessors.fromApplication(applicationContext, DownloadEntryPoint::class.java)
        // Touch repository so its init block attaches the listener.
        entry.downloadRepository()
        return entry.downloadManager()
    }

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

        return NotificationCompat.Builder(this, MyApplication.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setOngoing(activeCount > 0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val STOP_REASON_USER = 1

        fun startDownload(context: Context, contentId: String, manifestUrl: String) {
            val request = DownloadRequest.Builder(contentId, Uri.parse(manifestUrl))
                .setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                .build()
            sendAddDownload(context, DemoDownloadService::class.java, request, /* foreground = */ false)
        }

        fun pauseDownload(context: Context, id: String) {
            sendSetStopReason(
                context,
                DemoDownloadService::class.java,
                id,
                STOP_REASON_USER,
                /* foreground = */ false,
            )
        }

        fun resumeDownload(context: Context, id: String) {
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
}
