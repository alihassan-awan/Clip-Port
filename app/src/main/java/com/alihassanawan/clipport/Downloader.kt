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
import org.json.JSONObject
import java.io.File
import java.net.URI
import java.util.UUID

/** Phase 2a: playlist selection, reliable output selection and merged-audio preference. */
data class PlaylistVideo(val id: String, val title: String, val url: String)
data class PlaylistDetails(val title: String, val videos: List<PlaylistVideo>)

class Downloader(private val context: Context) {
    companion object {
        private const val TAG = "ClipPort"
        private val engineLock = Any()
        @Volatile private var updateAttempted = false

        fun isSupported(raw: String): Boolean = try {
            val uri = URI(raw.trim())
            val host = (uri.host ?: "").lowercase()
            uri.scheme.equals("https", true) &&
                (host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be")
        } catch (_: Exception) { false }

        fun isPlaylist(url: String): Boolean = try {
            val uri = URI(url.trim())
            isSupported(url) && uri.rawQuery.orEmpty().split('&').any {
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

    suspend fun analyze(url: String): String = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Enter a valid HTTPS YouTube URL" }
        ensureEngineReady()
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        YoutubeDL.getInstance().getInfo(request).title ?: "Untitled video"
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

    suspend fun download(url: String, choice: String, onProgress: (Float) -> Unit): String = withContext(Dispatchers.IO) {
        require(isSupported(url)) { "Invalid YouTube URL" }
        ensureEngineReady()
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: error("App download storage unavailable")
        val taskDir = File(baseDir, "tasks/${UUID.randomUUID()}")
        check(taskDir.mkdirs()) { "Cannot create temporary directory" }
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--no-overwrites")
            request.addOption("--restrict-filenames")
            request.addOption("--newline")
            request.addOption("-o", File(taskDir, "%(title).120B.%(ext)s").absolutePath)
            request.addOption("-f", selectFormat(choice))
            val audioOnly = choice == "Audio M4A"
            if (!audioOnly) request.addOption("--merge-output-format", "mp4")
            // Let yt-dlp finish post-processing before inspecting output files.
            // Some Android wrappers do not preserve --print output in result.out.
            YoutubeDL.getInstance().execute(request, UUID.randomUUID().toString()) { percent, _, _ ->
                onProgress(percent.coerceIn(0f, 100f))
            }
            // Use the actual finished files. Do not rely solely on yt-dlp stdout:
            // its wrapper may suppress or alter --print after_move output.
            val finalFile = locateCompletedMedia(taskDir, audioOnly)
                ?: error(
                    "No complete playable output with audio was found. " +
                    "Check yt-dlp/FFmpeg merging logs; do not save video-only files."
                )
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
            "Saved to Downloads/ClipPort/${finalFile.name}"
        } finally {
            taskDir.deleteRecursively()
        }
    }
}
