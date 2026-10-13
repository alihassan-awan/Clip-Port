package com.alihassanawan.clipport

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class Downloader(private val context: Context) {
    suspend fun analyze(url: String): String = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Please enter a valid YouTube link." }
        val info = YoutubeDL.getInstance().getInfo(url)
        info.title ?: "Untitled video"
    }

    suspend fun download(url: String, choice: String, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Invalid YouTube URL" }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "tasks/${UUID.randomUUID()}")
        require(dir.mkdirs()) { "Cannot create temporary folder" }
        try {
            val req = YoutubeDLRequest(url)
            req.addOption("--no-playlist")
            req.addOption("--no-overwrites")
            req.addOption("--restrict-filenames")
            req.addOption("--no-part", "")
            req.addOption("-o", File(dir, "%(title).150B.%(ext)s").absolutePath)
            if (choice == "Audio M4A") {
                req.addOption("-f", "bestaudio[ext=m4a]/bestaudio")
            } else {
                val format = when(choice) {
                    "Best" -> "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best"
                    else -> {
                        val height = choice.removeSuffix("p").toInt()
                        "bv*[height<=$height][ext=mp4]+ba[ext=m4a]/b[height<=$height][ext=mp4]/best[height<=$height]"
                    }
                }
                req.addOption("-f", format)
                req.addOption("--merge-output-format", "mp4")
            }
            YoutubeDL.getInstance().execute(req, { progress, _ -> onProgress(progress.coerceIn(0f, 100f)) })
            val file = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }?.maxByOrNull { it.length() }
                ?: error("Download finished but output file was not found")
            val isAudio = choice == "Audio M4A"
            val collection = if (isAudio) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, if (isAudio) "audio/mp4" else "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipPort")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: error("Failed to create media file")
            try {
                resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                    ?: error("Failed to write media file")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                }
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            "Saved to Downloads/ClipPort/${file.name}"
        } finally { dir.deleteRecursively() }
    }

    companion object {
        fun isSupported(raw: String): Boolean = try {
            val u = java.net.URI(raw.trim())
            val host = (u.host ?: "").lowercase()
            u.scheme == "https" && (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")
        } catch (_: Exception) { false }
    }
}
