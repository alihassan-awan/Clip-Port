
package com.alihassanawan.clipport

import android.app.Application
import com.yausername.youtubedl_android.YoutubeDL

class ClipPortApp : Application() {
    @Volatile
    var initializationError: String? = null

    override fun onCreate() {
        super.onCreate()

        Thread {
            try {
                YoutubeDL.getInstance().init(this)
            } catch (e: Exception) {
                initializationError =
                    e.message ?: "Download engine initialization failed"
            }
        }.start()
    }
}
