package com.mediawave.downloader.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.mediawave.downloader.MainActivity
import com.mediawave.downloader.R
import com.mediawave.downloader.data.model.DownloadQuality
import com.mediawave.downloader.data.model.DownloadRecord
import com.mediawave.downloader.data.model.DownloadStatus
import com.mediawave.downloader.data.repository.DownloadRepository
import com.mediawave.downloader.data.repository.UserPreferences
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var repository: DownloadRepository

    @Inject
    lateinit var prefs: UserPreferences

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "mediawave_downloads"
        const val CHANNEL_ID_COMPLETE = "mediawave_complete"
        const val NOTIFICATION_ID = 1001
        const val NOTIFICATION_ID_COMPLETE = 1002
        const val ACTION_STOP = "ACTION_STOP_DOWNLOAD"

        // Extra для фонового завантаження при share
        const val EXTRA_URL = "extra_url"

        fun start(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.startForegroundService(intent)
        }

        fun startWithUrl(context: Context, url: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, url)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, DownloadService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        val sharedUrl = intent?.getStringExtra(EXTRA_URL)

        if (sharedUrl != null) {
            // Share-режим: показуємо нотифікацію і відразу качаємо у фоні
            startForeground(NOTIFICATION_ID, buildNotification("Починаємо завантаження..."))
            startBackgroundDownload(sharedUrl)
        } else {
            // Звичайний режим: сервіс запущений з HomeViewModel
            startForeground(NOTIFICATION_ID, buildNotification("Починаємо завантаження..."))
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun startBackgroundDownload(url: String) {
        serviceScope.launch {
            try {
                val qualityStr = prefs.qualityFlow.first()
                val quality = when (qualityStr) {
                    "SD" -> DownloadQuality.SD
                    "AUDIO" -> DownloadQuality.AUDIO_ONLY
                    else -> DownloadQuality.HD
                }

                val activeCookie = repository.getCookieForUrl(url)

                downloadManager.startDownload(
                    url = url,
                    quality = quality,
                    cookieProfile = activeCookie,
                    onProgress = { download ->
                        val progressInt = (download.progress * 100).toInt()
                        updateNotification(
                            title = download.title,
                            progress = progressInt,
                            speed = download.speed,
                        )
                    },
                    onComplete = { filePath, title, author, thumbnail, extractor ->
                        serviceScope.launch {
                            repository.insertDownload(
                                DownloadRecord(
                                    title = title.ifBlank { url.substringAfterLast("/") },
                                    author = author.ifBlank { "Unknown" },
                                    sourceUrl = url,
                                    thumbnailUrl = thumbnail,
                                    filePath = filePath,
                                    quality = quality.label,
                                    extractor = extractor,
                                    status = DownloadStatus.COMPLETED,
                                )
                            )
                            notifyDownloadComplete(title.ifBlank { url.substringAfterLast("/") })
                            stopSelf()
                        }
                    },
                    onError = { error ->
                        notifyDownloadError(error)
                        stopSelf()
                    },
                )
            } catch (e: Exception) {
                notifyDownloadError(e.message ?: getString(R.string.download_failed))
                stopSelf()
            }
        }
    }

    fun updateNotification(title: String, progress: Int, speed: String) {
        val notification = buildProgressNotification(title, progress, speed)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun notifyDownloadComplete(title: String) {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setContentTitle(getString(R.string.download_complete))
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            manager.notify(NOTIFICATION_ID_COMPLETE, notification)
        }
    }

    fun notifyDownloadError(error: String) {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID_COMPLETE)
            .setContentTitle(getString(R.string.download_failed))
            .setContentText(error)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        ) {
            manager.notify(NOTIFICATION_ID_COMPLETE + 1, notification)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val progressChannel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_desc)
            setSound(null, null)
        }

        val completeChannel = NotificationChannel(
            CHANNEL_ID_COMPLETE,
            "Завершення завантаження",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Сповіщення про завершення або помилку завантаження"
        }

        manager.createNotificationChannel(progressChannel)
        manager.createNotificationChannel(completeChannel)
    }

    private fun buildNotification(content: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MediaWave")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun buildProgressNotification(title: String, progress: Int, speed: String): Notification {
        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, DownloadService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$speed • $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_delete, "Скасувати", stopPendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
