package com.alihassanawan.clipport

import android.app.Application
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg

class ClipPortApp : Application() {
    @Volatile var initializationError: String? = null
    override fun onCreate() {
        super.onCreate()
        Thread {
            try {
                YoutubeDL.getInstance().init(applicationContext)
                FFmpeg.getInstance().init(applicationContext)
                Log.i("ClipPort", "yt-dlp and FFmpeg initialized")
            } catch (e: Exception) {
                initializationError = e.message ?: "Engine initialization failed"
                Log.e("ClipPort", "Engine init failed", e)
            }
        }.start()
    }
}
