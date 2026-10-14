package com.alihassanawan.clipport

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

/** Phase 2a: playlist selection, reliable output selection and merged-audio preference. */
data class PlaylistVideo(val id: String, val title: String, val url: String)
data class MediaPreview(val title: String, val thumbnail: String?, val source: String)
data class PlaylistDetails(val title: String, val videos: List<PlaylistVideo>)

class Downloader(private val context: Context) {
    companion object {
        private const val TAG = "ClipPort"
        private val engineLock = Any()
        @Volatile private var updateAttempted = false

        // Explicit allowlist: do not accept arbitrary sites or misleading subdomains.
        fun isSupported(raw: String): Boolean = try {
            val uri = URI(raw.trim())
            val host = uri.host.orEmpty().lowercase().trimEnd('.')
            uri.scheme.equals("https", true) && uri.userInfo == null &&
                uri.port == -1 && host in setOf(
                    "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be",
                    "facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com", "fb.watch",
                    "tiktok.com", "www.tiktok.com", "m.tiktok.com", "vm.tiktok.com", "vt.tiktok.com"
                )
        } catch (_: Exception) { false }

        fun source(raw: String): String = try {
            when (URI(raw.trim()).host.orEmpty().lowercase()) {
                "youtu.be", "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> "YouTube"
                "facebook.com", "www.facebook.com", "m.facebook.com", "web.facebook.com", "fb.watch" -> "Facebook"
                else -> "TikTok"
            }
        } catch (_: Exception) { "Video" }

        fun isPlaylist(url: String): Boolean = try {
            val uri = URI(url.trim())
            source(url) == "YouTube" && isSupported(url) && uri.rawQuery.orEmpty().split('&').any {
                it.substringBefore('=') == "list" && it.substringAfter('=', "").isNotBlank()
            }
        } catch (_: Exception) { false }
    }

    private fun ensureEngineReady() {
        synchronized(engineLock) {
            val engine = YoutubeDL.getInstance()
            engine.init(context.applicationContext)
            if (!updateAttempted) {
                updateAttempted = true
                try {
                    Log.i(TAG, "yt-dlp update: ${engine.updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)}")
                } catch (e: Exception) {
                    // Do not confuse a failed update check with a successful update.
                    Log.w(TAG, "yt-dlp update unavailable; using bundled version", e)
                }
            }
        }
    }

    private fun writeDiagnostics(taskDir: File, message: String) {
        try {
            File(taskDir, "clipport-diagnostics.txt").appendText(
                "\n--- ${System.currentTimeMillis()} ---\n$message\n"
            )
            Log.e(TAG, message.takeLast(4000))
        } catch (e: Exception) {
            Log.e(TAG, "Unable to save diagnostics", e)
        }
    }

    suspend fun analyze(url: String): String = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Enter a valid HTTPS YouTube URL" }
        ensureEngineReady()
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        YoutubeDL.getInstance().getInfo(request).title ?: "Untitled video"
    }

    suspend fun analyzeMedia(url: String): MediaPreview = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Enter a public HTTPS YouTube, Facebook or TikTok link" }
        ensureEngineReady()
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--skip-download")
        request.addOption("--dump-single-json")
        val output = YoutubeDL.getInstance().execute(request).out
        val jsonStart = output.indexOf('{')
        check(jsonStart >= 0) { "Could not read video metadata" }
        val data = JSONObject(output.substring(jsonStart))
        MediaPreview(
            data.optString("title").takeIf { it.isNotBlank() } ?: "Untitled video",
            data.optString("thumbnail").takeIf { it.startsWith("https://") },
            source(url)
        )
    }

    suspend fun analyzePlaylist(url: String): PlaylistDetails = withContext(Dispatchers.IO) {
        require(isPlaylist(url)) { "Enter a YouTube playlist URL containing list=" }
        ensureEngineReady()
        val request = YoutubeDLRequest(url)
        request.addOption("--yes-playlist")
        request.addOption("--flat-playlist")
        request.addOption("--playlist-end", "50")
        request.addOption("--dump-single-json")
        val result = YoutubeDL.getInstance().execute(request)
        val json = JSONObject(result.out)
        val entries = json.optJSONArray("entries") ?: error("Playlist entries unavailable")
        val videos = buildList {
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                val id = entry.optString("id")
                if (!id.matches(Regex("[A-Za-z0-9_-]{11}"))) continue
                add(PlaylistVideo(id, entry.optString("title", "Video ${i + 1}"), "https://www.youtube.com/watch?v=$id"))
            }
        }
        require(videos.isNotEmpty()) { "No available videos in this playlist" }
        PlaylistDetails(json.optString("title", "Playlist"), videos)
    }

    /** Slash fallback only chooses streams with audio in the last branch. */
    private fun selectFormat(choice: String): String = when (choice) {
        "Audio M4A" -> "bestaudio[ext=m4a]/bestaudio"
        "Best" -> "bv[ext=mp4]+ba[ext=m4a]/bv+ba/best"
        "1080p", "720p", "480p", "360p" -> {
            val limit = choice.removeSuffix("p").toInt()
            "bv[height<=$limit][ext=mp4]+ba[ext=m4a]/" +
                "bv[height<=$limit]+ba/b[height<=$limit]"
        }
        else -> error("Unsupported quality: $choice")
    }

    /** Prefer completed media with audio; exclude subtitle, temporary and video-only fragments. */
    private fun locateCompletedMedia(dir: File, audioOnly: Boolean): File? {
        val candidates = dir.listFiles().orEmpty()
            .filter { file ->
                file.isFile && file.length() > 0 &&
                file.extension.lowercase() in setOf("mp4", "m4a", "webm", "mkv", "mp3") &&
                !file.name.endsWith(".part", ignoreCase = true)
            }
            .sortedByDescending { it.length() }

        return candidates.firstOrNull { candidate ->
            val ext = candidate.extension.lowercase()
            if (audioOnly && ext in setOf("m4a", "mp3")) return@firstOrNull true
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(candidate.absolutePath)
                // Android should never save a silent video as a successful download.
                val hasAudio = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO
                ) == "yes"
                val hasVideo = retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO
                ) == "yes"
                hasAudio && (audioOnly || hasVideo)
            } catch (e: Exception) {
                Log.w(TAG, "Cannot validate media track: ${candidate.name}", e)
                false
            } finally {
                try { retriever.release() } catch (_: Exception) { }
            }
        }
    }

    suspend fun download(url: String, choice: String, onProgress: (Float) -> Unit, processId: String = UUID.randomUUID().toString()): String = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Invalid YouTube URL" }
        ensureEngineReady()
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("App download storage unavailable")
        val taskDir = File(baseDir, "tasks/${UUID.randomUUID()}")
        check(taskDir.mkdirs()) { "Cannot create temporary directory" }
        var completedSuccessfully = false
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-overwrites")
            request.addOption("--restrict-filenames")
            request.addOption("--newline")
            request.addOption("-o", File(taskDir, "%(title).120B.%(ext)s").absolutePath)
            val source = source(url)
            val format = if (source == "YouTube") selectFormat(choice) else when (choice) {
                "Audio M4A" -> "bestaudio[ext=m4a]/bestaudio/best"
                "Best" -> "best[ext=mp4]/bestvideo+bestaudio/best"
                else -> {
                    val limit = choice.removeSuffix("p").toIntOrNull() ?: 720
                    "best[height<=$limit][ext=mp4]/best[height<=$limit]/" +
                        "bestvideo[height<=$limit]+bestaudio/best[ext=mp4]/best"
                }
            }
            request.addOption("-f", format)
            val audioOnly = choice == "Audio M4A"
            if (!audioOnly) request.addOption("--merge-output-format", "mp4")
            else if (source != "YouTube") {
                request.addOption("--extract-audio")
                request.addOption("--audio-format", "m4a")
            }
            // Let yt-dlp finish post-processing before inspecting output files.
            // Some Android wrappers do not preserve --print output in result.out.
            val response = try {
                YoutubeDL.getInstance().execute(request, processId) { percent, _, _ ->
                    onProgress(percent.coerceIn(0f, 100f))
                }
            } catch (e: Exception) {
                // Keep diagnostic information even if yt-dlp itself fails.
                writeDiagnostics(taskDir, "Download engine exception: ${e.stackTraceToString()}")
                throw e
            }
            if (response.err.contains("ERROR:", ignoreCase = true)) {
                writeDiagnostics(taskDir, "yt-dlp warning/error: ${response.err.takeLast(6000)}")
            }
            // Use the actual finished files. Do not rely solely on yt-dlp stdout:
            // its wrapper may suppress or alter --print after_move output.
            val finalFile = locateCompletedMedia(taskDir, audioOnly)
                ?: run {
                    writeDiagnostics(taskDir, "Media verification failed. Available files: " +
                        taskDir.listFiles().orEmpty().joinToString { "${it.name} (${it.length()} bytes)" })
                    error("No verified audio+video output. Diagnostic log saved in app storage. " +
                        "Please share ClipPort diagnostics for troubleshooting.")
                }
            val ext = finalFile.extension.lowercase()
            val mime = when(ext) {
                "mp4", "m4v" -> "video/mp4"
                "m4a" -> "audio/mp4"
                "mp3" -> "audio/mpeg"
                "webm" -> if (audioOnly) "audio/webm" else "video/webm"
                "mkv" -> "video/x-matroska"
                else -> "application/octet-stream"
            }
            // Downloads collection, not Video collection: Android forbids Download/ in Video.
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, finalFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipPort")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val output = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create destination file")
            try {
                resolver.openOutputStream(output)?.use { target ->
                    finalFile.inputStream().use { it.copyTo(target) }
                } ?: error("Unable to write destination file")
                resolver.update(output, ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }, null, null)
            } catch (e: Exception) {
                resolver.delete(output, null, null)
                throw e
            }
            onProgress(100f)
            completedSuccessfully = true
            "Saved to Downloads/ClipPort/${finalFile.name}"
        } finally {
            if (completedSuccessfully) taskDir.deleteRecursively()
            else {
                val log = File(taskDir, "clipport-diagnostics.txt")
                if (log.isFile) {
                    try {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "clipport-log-${taskDir.name}.txt")
                            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipPort")
                        }
                        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        if (uri != null) context.contentResolver.openOutputStream(uri)?.use { out ->
                            log.inputStream().use { it.copyTo(out) }
                        }
                    } catch (e: Exception) { Log.e(TAG, "Could not export diagnostic log", e) }
                }
                taskDir.deleteRecursively()
            }
        }
    }
}
