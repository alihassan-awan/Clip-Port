package com.alihassanawan.clipport

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** User-authorized, local-only import. Cannot read private WhatsApp storage. */
internal data class StatusMedia(val uri: Uri, val name: String, val mime: String)

internal object StatusStorage {
    private const val PREF = "clipport_status_folder"
    private const val KEY = "uri"
    fun getFolder(context: Context): Uri? = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        .getString(KEY, null)?.let(Uri::parse)

    fun connect(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, uri.toString()).apply()
    }

    fun disconnect(context: Context) {
        getFolder(context)?.let { uri ->
            runCatching { context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    suspend fun scan(context: Context, folder: Uri): List<StatusMedia> = withContext(Dispatchers.IO) {
        DocumentFile.fromTreeUri(context, folder)?.listFiles().orEmpty()
            .filter { it.isFile && isMedia(it.type) }
            .take(150)
            .map { StatusMedia(it.uri, it.name ?: "Status", it.type ?: "application/octet-stream") }
    }

    private fun isMedia(mime: String?) = mime?.startsWith("image/") == true || mime?.startsWith("video/") == true

    suspend fun importMedia(context: Context, source: Uri): String = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = resolver.getType(source) ?: error("Unsupported media type")
        require(isMedia(mime)) { "Select an image or video file" }
        val extension = when (mime) {
            "image/jpeg" -> "jpg"
            "image/png" -> "png"
            "image/webp" -> "webp"
            "video/mp4" -> "mp4"
            "video/3gpp" -> "3gp"
            else -> error("Unsupported media format: $mime")
        }
        val name = "WhatsApp_Status_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.$extension"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClipPort/Statuses")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val dest = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("Unable to create destination file")
        try {
            resolver.openInputStream(source)?.use { input ->
                resolver.openOutputStream(dest)?.use { output -> input.copyTo(output) }
                    ?: error("Cannot open output file")
            } ?: error("Cannot open selected file")
            resolver.update(dest, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            resolver.delete(dest, null, null)
            throw e
        }
        "Saved in Downloads/ClipPort/Statuses/$name"
    }
}

@Composable
internal fun WhatsAppStatusScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folder by remember { mutableStateOf(StatusStorage.getFolder(context)) }
    var media by remember { mutableStateOf<List<StatusMedia>>(emptyList()) }
    var message by remember { mutableStateOf("Only the files you explicitly select are accessed. No WhatsApp login or account access.") }
    var running by remember { mutableStateOf(false) }

    fun scanFolder(uri: Uri) {
        scope.launch {
            running = true
            try {
                media = StatusStorage.scan(context, uri)
                message = if (media.isEmpty()) "No accessible images/videos found. WhatsApp may hide this folder; use Import status files." else "${media.size} accessible status files found"
            } catch (e: Exception) { message = "Folder access unavailable: ${e.message}" }
            finally { running = false }
        }
    }

    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                StatusStorage.connect(context, uri)
                folder = uri
                scanFolder(uri)
            } catch (e: Exception) { message = "Folder permission failed: ${e.message}" }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                running = true
                var saved = 0
                uris.take(50).forEach { uri ->
                    try { StatusStorage.importMedia(context, uri); saved++ }
                    catch (_: Exception) { }
                }
                message = "$saved of ${uris.size} selected files saved locally in Downloads/ClipPort/Statuses"
                running = false
            }
        }
    }
    LaunchedEffect(folder) { folder?.let { scanFolder(it) } }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(color = Color(0xFF16213A), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("WhatsApp Status Saver", style = MaterialTheme.typography.headlineSmall)
                Text("Import already-viewed status media from a folder Android lets you access, or choose files individually. No background WhatsApp scanning.", color = Color(0xFFB3BDCC), fontSize = 13.sp)
                Button(onClick = { treePicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Choose Status Folder")
                }
                OutlinedButton(onClick = { filePicker.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.FileDownload, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Import Status Files")
                }
                if (folder != null) {
                    Row {
                        TextButton(onClick = { folder?.let { scanFolder(it) } }) { Text("Refresh") }
                        TextButton(onClick = { StatusStorage.disconnect(context); folder = null; media = emptyList(); message = "Folder disconnected" }) { Text("Disconnect folder") }
                    }
                }
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(message, color = Color(0xFFB3BDCC), fontSize = 12.sp)
            }
        }
        media.forEach { item ->
            Surface(color = Color(0xFF16213A), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (item.mime.startsWith("image/")) {
                        AsyncImage(model = item.uri, contentDescription = "Status preview", modifier = Modifier.size(80.dp), contentScale = ContentScale.Crop)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text(item.name, maxLines = 2, fontSize = 12.sp)
                        Text(item.mime, fontSize = 10.sp, color = Color(0xFFB3BDCC))
                    }
                    TextButton(onClick = {
                        scope.launch {
                            running = true
                            message = try { StatusStorage.importMedia(context, item.uri) }
                            catch (e: Exception) { "Save failed: ${e.message}" }
                            finally { running = false }
                        }
                    }) { Text("Save") }
                }
            }
        }
    }
}
