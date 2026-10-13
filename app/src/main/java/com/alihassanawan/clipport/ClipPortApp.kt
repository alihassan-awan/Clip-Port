package com.alihassanawan.clipport

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.ffmpeg.FFmpeg

class ClipPortApp : Application() {
    @Volatile var initializationError: String? = null
    override fun onCreate() {
        super.onCreate()
        Thread {
            try {
                YoutubeDL.getInstance().init(this)
                FFmpeg.getInstance().init(this)
            } catch (e: Exception) { initializationError = e.message ?: "Download engine initialization failed" }
        }.start()
    }
}
