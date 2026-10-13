
package com.alihassanawan.clipport

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.util.UUID

class Downloader(private val context: Context) {

    companion object {
        private const val TAG = "ClipPort"

        private val engineLock = Any()

        @Volatile
        private var updateAttempted = false

        fun isSupported(raw: String): Boolean {
            return try {
                val uri = URI(raw.trim())
                val host = (uri.host ?: "").lowercase()

                uri.scheme.equals(
                    "https",
                    ignoreCase = true
                ) && (
                    host == "youtube.com" ||
                    host.endsWith(".youtube.com") ||
                    host == "youtu.be"
                )
            } catch (e: Exception) {
                false
            }
        }
    }

    // Initialize yt-dlp and check for an update.
    // Called from Dispatchers.IO.
    private fun ensureEngineReady() {
        synchronized(engineLock) {
            val engine = YoutubeDL.getInstance()

            engine.init(context.applicationContext)

            if (!updateAttempted) {
                updateAttempted = true

                try {
                    val result = engine.updateYoutubeDL(
                        context.applicationContext,
                        YoutubeDL.UpdateChannel.STABLE
                    )

                    Log.i(
                        TAG,
                        "yt-dlp update result: $result"
                    )

                } catch (e: Exception) {
                    Log.w(
                        TAG,
                        "yt-dlp update failed; using installed version",
                        e
                    )
                }
            }
        }
    }

    // Analyze a single YouTube video.
    suspend fun analyze(url: String): String =
        withContext(Dispatchers.IO) {

            require(isSupported(url)) {
                "Please enter a valid HTTPS YouTube URL."
            }

            ensureEngineReady()

            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")

            val info = YoutubeDL.getInstance().getInfo(request)

            info.title ?: "Untitled video"
        }

    // Select a video/audio format.
    private fun selectFormat(choice: String): String {
        return when (choice) {

            "Best" -> {
                "bv[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/best"
            }

            "Audio M4A" -> {
                "bestaudio[ext=m4a]/bestaudio"
            }

            "1080p", "720p", "480p", "360p" -> {
                val height = choice.removeSuffix("p").toInt()

                "bv[height<=$height][ext=mp4]" +
                    "+ba[ext=m4a]" +
                    "/b[height<=$height][ext=mp4]" +
                    "/best[height<=$height]"
            }

            else -> {
                "b[ext=mp4]/best"
            }
        }
    }

    // Download a single video to the Android device.
    suspend fun download(
        url: String,
        choice: String,
        onProgress: (Float) -> Unit
    ): String = withContext(Dispatchers.IO) {

        require(isSupported(url)) {
            "Invalid YouTube URL"
        }

        ensureEngineReady()

        val baseDir = context.getExternalFilesDir(
            Environment.DIRECTORY_DOWNLOADS
        ) ?: error("App download storage unavailable")

        val taskDir = File(
            baseDir,
            "tasks/${UUID.randomUUID()}"
        )

        check(taskDir.mkdirs()) {
            "Cannot create temporary download folder"
        }

        try {
            val request = YoutubeDLRequest(url)

            request.addOption("--no-playlist")
            request.addOption("--no-overwrites")
            request.addOption("--restrict-filenames")
            request.addOption("--newline")

            request.addOption(
                "-o",
                File(
                    taskDir,
                    "%(title).150B.%(ext)s"
                ).absolutePath
            )

            val isAudio = choice == "Audio M4A"

            request.addOption(
                "-f",
                selectFormat(choice)
            )

            if (!isAudio) {
                request.addOption(
                    "--merge-output-format",
                    "mp4"
                )
            }

            val processId = UUID.randomUUID().toString()

            Log.i(TAG, "Starting download process")

            YoutubeDL.getInstance().execute(
                request,
                processId,
                { progress, _, _ ->
                    onProgress(
                        progress.coerceIn(0f, 100f)
                    )
                }
            )

            // Locate the final media output.
            val outputFile = taskDir.listFiles()
                ?.filter { file ->
                    file.isFile &&
                    !file.name.endsWith(".part") &&
                    !file.name.endsWith(".ytdl") &&
                    !file.name.endsWith(".temp")
                }
                ?.maxByOrNull { it.length() }
                ?: error(
                    "Download completed but media file was not found"
                )

            // Determine the actual output MIME type.
            val extension = outputFile.extension.lowercase()

            val mimeType = when (extension) {
                "mp4", "m4v" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "webm" -> if (isAudio) {
                    "audio/webm"
                } else {
                    "video/webm"
                }
                else -> "application/octet-stream"
            }

            val collection = if (isAudio) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            // Save the completed file to Downloads/ClipPort.
            val values = ContentValues().apply {
                put(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    outputFile.name
                )

                put(
                    MediaStore.MediaColumns.MIME_TYPE,
                    mimeType
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS +
                            "/ClipPort"
                    )

                    put(
                        MediaStore.MediaColumns.IS_PENDING,
                        1
                    )
                }
            }

            val resolver = context.contentResolver

            val outputUri = resolver.insert(
                collection,
                values
            ) ?: error("Failed to create output media file")

            try {
                val stream = resolver.openOutputStream(outputUri)
                    ?: error("Failed to open output stream")

                stream.use { output ->
                    outputFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val completedValues = ContentValues().apply {
                        put(
                            MediaStore.MediaColumns.IS_PENDING,
                            0
                        )
                    }

                    resolver.update(
                        outputUri,
                        completedValues,
                        null,
                        null
                    )
                }

            } catch (e: Exception) {
                resolver.delete(
                    outputUri,
                    null,
                    null
                )

                throw e
            }

            onProgress(100f)

            Log.i(
                TAG,
                "Download saved: ${outputFile.name}"
            )

            "Saved to Downloads/ClipPort/${outputFile.name}"

        } finally {
            // Remove temporary files regardless of result.
            taskDir.deleteRecursively()
        }
    }
}
