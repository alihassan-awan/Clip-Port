package com.alihassanawan.clipport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import android.util.Log
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-started foreground service. One download at a time, so yt-dlp/FFmpeg stays bounded. */
class DownloadService : Service() {
    companion object {
        const val START = "com.alihassanawan.clipport.START"
        const val CANCEL = "com.alihassanawan.clipport.CANCEL"
        const val RETRY = "com.alihassanawan.clipport.RETRY"
        const val ID = "jobId"
        private const val CHANNEL = "clipport-downloads"
        private const val NOTIFICATION_ID = 1042
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var processing: Job? = null
    private var currentId: String? = null
    private var cancelling = false
    private val manager by lazy { getSystemService(NotificationManager::class.java) }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "ClipPort downloads", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == CANCEL) {
            val id = intent.getStringExtra(ID)
            if (id != null) cancelJob(id)
            return START_NOT_STICKY
        }
        if (action == RETRY) intent.getStringExtra(ID)?.let { DownloadQueue.retry(it) }
        if (DownloadQueue.items.value.none { it.state == "Queued" || it.state == "Downloading" }) {
            if (currentId == null) stopSelf()
            return START_NOT_STICKY
        }
        val type = if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Preparing downloads",0), type)
        if (processing?.isActive != true) processing = scope.launch { processQueue() }
        return START_NOT_STICKY
    }
    private fun notification(message: String, percent: Int): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this,MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this,CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("ClipPort")
            .setContentText(message.take(70))
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent.coerceIn(0,100),false)
            .build()
    }
    private fun cancelJob(id: String) {
        val item = DownloadQueue.get(id) ?: return
        if (item.state == "Queued") {
            DownloadQueue.update(id) { it.copy(state="Cancelled",message="Removed from queue") }
        } else if (id == currentId) {
            cancelling = true
            YoutubeDL.getInstance().destroyProcessById(id)
        }
    }
    private suspend fun processQueue() {
        val downloader = Downloader(applicationContext)
        try {
            while (true) {
                val next = DownloadQueue.items.value.firstOrNull { it.state == "Queued" } ?: break
                currentId = next.id
                cancelling = false
                DownloadQueue.update(next.id) { it.copy(state="Downloading",progress=0f) }
                manager.notify(NOTIFICATION_ID,notification(next.title,0))
                try {
                    // A single bounded retry for transient 403 failures. A persistent
                    // rejection is surfaced clearly and the next playlist item continues.
                    var message: String? = null
                    for (attempt in 0..1) {
                        try {
                            message = downloader.download(next.url, next.quality, { value ->
                                DownloadQueue.update(next.id) { it.copy(progress=value) }
                                if (value.toInt() % 5 == 0) manager.notify(NOTIFICATION_ID,notification(next.title,value.toInt()))
                            }, next.id)
                            break
                        } catch (e: Exception) {
                            val forbidden = e.message?.contains("403") == true &&
                                e.message?.contains("Forbidden", ignoreCase = true) == true
                            if (!forbidden || attempt == 1 || cancelling) {
                                if (forbidden && !cancelling) {
                                    throw IllegalStateException(
                                        "YouTube rejected this video's media request (HTTP 403). " +
                                        "Update yt-dlp and retry later; some streams are unavailable.", e
                                    )
                                }
                                throw e
                            }
                            Log.w("ClipPort", "HTTP 403 for ${next.id}; retrying once")
                            DownloadQueue.update(next.id) {
                                it.copy(progress=0f, message="HTTP 403: retrying once...")
                            }
                            delay(1500L)
                        }
                    }
                    DownloadQueue.update(next.id) { it.copy(state="Completed",progress=100f,message=message ?: "Saved") }
                } catch(e: Exception) {
                    DownloadQueue.update(next.id) {
                        it.copy(state=if(cancelling) "Cancelled" else "Failed",message=if(cancelling) "Cancelled by user" else (e.message ?: "Download failed").take(180))
                    }
                } finally { currentId = null; cancelling = false }
            }
        } finally {
            ServiceCompat.stopForeground(this,ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }
    override fun onDestroy() {
        currentId?.let { YoutubeDL.getInstance().destroyProcessById(it) }
        scope.cancel()
        super.onDestroy()
    }
}
