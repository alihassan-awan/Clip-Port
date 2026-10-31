package com.alihassanawan.clipport

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.UUID

/** Best-effort, public Instagram single-photo HTML metadata fallback.
 * This is NOT a private-post downloader or full carousel extractor.
 */
internal object InstagramPhoto {
    data class Photo(val title: String, val imageUrl: String)

    private fun photoPost(url: String): Boolean = Downloader.isSupported(url) &&
        Downloader.source(url) == "Instagram" && URI(url).path.orEmpty().startsWith("/p/")

    private fun allowedImageHost(url: String): Boolean = try {
        val uri = URI(url)
        val host = uri.host.orEmpty().lowercase()
        uri.scheme == "https" && uri.userInfo == null && uri.port == -1 &&
            (host == "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
             host == "fbcdn.net" || host.endsWith(".fbcdn.net"))
    } catch (_: Exception) { false }

    private fun attribute(tag: String, key: String): String? {
        val regex = Regex("""\b${Regex.escape(key)}\s*=\s*(["'])(.*?)\1""", RegexOption.IGNORE_CASE)
        return regex.find(tag)?.groupValues?.get(2)
    }

    private fun decode(value: String) = value.replace("&amp;", "&")
        .replace("&quot;", "\"").replace("&#39;", "'")

    suspend fun inspect(url: String): Photo? = withContext(Dispatchers.IO) {
        if (!photoPost(url)) return@withContext null
        var nextUrl = url
        var html: String? = null
        for (attempt in 0 until 4) {
            val conn = (URL(nextUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 8000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html")
            }
            try {
                if (conn.responseCode in 300..399) {
                    val location = conn.getHeaderField("Location") ?: return@withContext null
                    val redirect = URI(nextUrl).resolve(location).toString()
                    if (!Downloader.isSupported(redirect) || Downloader.source(redirect) != "Instagram") return@withContext null
                    nextUrl = redirect
                } else if (conn.responseCode == 200 && conn.contentType.orEmpty().contains("text/html")) {
                    html = conn.inputStream.reader().use { reader ->
                        val buffer = CharArray(4096)
                        val out = StringBuilder()
                        while (out.length < 2_000_000) {
                            val len = reader.read(buffer)
                            if (len < 0) break
                            out.append(buffer, 0, len)
                        }
                        out.toString()
                    }
                    break
                } else return@withContext null
            } finally { conn.disconnect() }
            if (html != null) break
        }
        val page = html ?: return@withContext null
        val meta = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(page).map { it.value }.toList()
        // Never save video cover art as if it were the original video.
        val video = meta.any { tag ->
            val prop = attribute(tag, "property").orEmpty()
            prop.startsWith("og:video") || prop.startsWith("video:")
        }
        if (video) return@withContext null
        val image = meta.firstNotNullOfOrNull { tag ->
            val prop = attribute(tag, "property") ?: attribute(tag, "name")
            if (prop.equals("og:image", ignoreCase = true)) attribute(tag, "content") else null
        }?.let(::decode) ?: return@withContext null
        if (!allowedImageHost(image)) return@withContext null
        val title = meta.firstNotNullOfOrNull { tag ->
            if (attribute(tag, "property").equals("og:title", true)) attribute(tag, "content") else null
        }?.let(::decode)?.take(140) ?: "Instagram photo"
        Photo(title, image)
    }

    suspend fun save(context: Context, photo: Photo): String = withContext(Dispatchers.IO) {
        require(allowedImageHost(photo.imageUrl)) { "Untrusted image host" }
        val conn = (URL(photo.imageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 30000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36")
        }
        try {
            check(conn.responseCode == 200) { "Instagram image unavailable (HTTP ${conn.responseCode})" }
            val mime = conn.contentType.orEmpty().substringBefore(';')
            require(mime in setOf("image/jpeg", "image/png", "image/webp")) { "Unexpected image format" }
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
            val name = "Instagram_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$ext"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipPort")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Unable to save image")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    conn.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= 50L * 1024 * 1024) { "Image exceeds 50MB limit" }
                            output.write(buffer, 0, read)
                        }
                        require(total > 0) { "Empty image" }
                    }
                } ?: error("Unable to write output")
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
            "Saved to Downloads/ClipPort/$name"
        } finally { conn.disconnect() }
    }
}
