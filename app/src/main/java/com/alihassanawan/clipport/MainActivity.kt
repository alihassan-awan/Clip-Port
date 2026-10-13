package com.alihassanawan.clipport

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.net.URI

private val Night = Color(0xFF090E1C)
private val Panel = Color(0xFF16213A)
private val Soft = Color(0xFF202C49)
private val Violet = Color(0xFF9587FF)
private val Cyan = Color(0xFF57D6FF)
private val Pale = Color(0xFFF4F5FF)
private val Subtle = Color(0xFFA9B6CD)
private val choices = listOf("Best", "1080p", "720p", "480p", "360p", "Audio M4A")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { PremiumScreen(initial) }
    }
}

private fun ytId(raw: String): String? = try {
    val uri = URI(raw)
    val host = uri.host.orEmpty().lowercase()
    val id = if (host == "youtu.be") uri.path.trim('/') else
        uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.substringAfter('=')
    id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
} catch (_: Exception) { null }

@Composable
private fun PreviewImage(videoId: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Soft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        if (videoId != null) AsyncImage(
            model = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            contentDescription = "Video thumbnail",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        ) else Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Cyan)
    }
}

@Composable
private fun PremiumScreen(initialUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { Downloader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val jobs by DownloadQueue.items.collectAsState()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<PlaylistDetails?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var quality by remember { mutableStateOf("Best") }
    var qualityMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Paste a video or playlist link to get started.") }
    var update by remember { mutableStateOf<ClipPortRelease?>(null) }
    var updateMessage by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }
    fun startQueue() {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(DownloadService.START))
    }
    fun checkUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                update = UpdateChecker.check(context)
                updateMessage = if (update == null) "No newer GitHub Release found." else "New version available: ${update!!.tag}"
            } catch (e: Exception) { updateMessage = "Update check unavailable: ${e.message ?: "network error"}" }
            finally { checking = false }
        }
    }
    LaunchedEffect(Unit) { checkUpdate() }
    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Cyan,
        background = Night, surface = Panel, onSurface = Pale, onBackground = Pale)) {
        Surface(color = Night, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp).background(Brush.linearGradient(listOf(Violet, Color(0xFF5368FF))), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Text("ClipPort", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Built by Ali Hassan Awan", color = Subtle, fontSize = 11.sp)
                        }
                        IconButton(onClick = { checkUpdate() }) {
                            Icon(Icons.Default.SystemUpdate, contentDescription = "Check for updates", tint = Cyan)
                        }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1D2851), Panel)), RoundedCornerShape(26.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Your media, one tap away.", fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
                        Text("Choose your quality. Save to your phone. Keep downloads running in the background.", color = Subtle, fontSize = 13.sp)
                        OutlinedTextField(value = url, onValueChange = { url = it; title = ""; playlist = null; selected.clear() },
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste a YouTube link") },
                            singleLine = true, shape = RoundedCornerShape(15.dp),
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) })
                        Button(onClick = {
                            busy = true; title = ""; playlist = null; selected.clear(); status = "Analyzing media..."
                            scope.launch {
                                try {
                                    if (Downloader.isPlaylist(url)) {
                                        val p = downloader.analyzePlaylist(url)
                                        playlist = p; selected.addAll(p.videos.map { it.id }); status = "${p.videos.size} videos found"
                                    } else { title = downloader.analyze(url); status = "Ready to download" }
                                } catch (e: Exception) { status = "Analysis failed: ${e.message ?: "Unknown error"}" }
                                finally { busy = false }
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(9.dp)); Text(if (busy) "Analyzing..." else "Analyze link", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (playlist != null || title.isNotBlank()) item {
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("MEDIA PREVIEW", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        if (playlist == null) {
                            PreviewImage(ytId(url), Modifier.fillMaxWidth().height(190.dp))
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(playlist!!.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("${playlist!!.videos.size} videos in this playlist", color = Subtle)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Save quality", color = Subtle, modifier = Modifier.weight(1f))
                            Box {
                                OutlinedButton(onClick = { qualityMenu = true }) { Text(quality); Icon(Icons.Default.ArrowDropDown, contentDescription = null) }
                                DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                                    choices.forEach { q -> DropdownMenuItem(text = { Text(q) }, onClick = { quality = q; qualityMenu = false }) }
                                }
                            }
                        }
                        playlist?.let { p ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${selected.size} selected", color = Subtle, modifier = Modifier.weight(1f))
                                TextButton(onClick = { selected.clear(); selected.addAll(p.videos.map { it.id }) }) { Text("All") }
                                TextButton(onClick = { selected.clear() }) { Text("None") }
                            }
                            p.videos.forEach { v ->
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                    if (v.id in selected) selected.remove(v.id) else selected.add(v.id)
                                }) {
                                    Checkbox(checked = v.id in selected, onCheckedChange = {
                                        if (it) { if (v.id !in selected) selected.add(v.id) } else selected.remove(v.id)
                                    })
                                    PreviewImage(v.id, Modifier.size(width = 96.dp, height = 58.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Text(v.title, modifier = Modifier.weight(1f), fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                                HorizontalDivider(color = Soft)
                            }
                        }
                        Button(onClick = {
                            val toAdd = playlist?.videos?.filter { it.id in selected }?.map { DownloadItem(url=it.url, quality=quality, title=it.title) }
                                ?: listOf(DownloadItem(url=url, quality=quality, title=title))
                            DownloadQueue.enqueue(toAdd); startQueue(); status = "${toAdd.size} item(s) added to queue"
                        }, enabled = playlist == null || selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null); Spacer(Modifier.width(8.dp))
                            Text(if (playlist == null) "Download to phone" else "Download selected (${selected.size})")
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Cyan)
                        Text(status, color = Subtle, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
                if (update != null || updateMessage.isNotBlank()) item {
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(updateMessage, fontSize = 13.sp)
                        update?.let { release -> Button(onClick = { UpdateChecker.openRelease(context, release) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("View verified GitHub Release")
                        } }
                    }
                }
                if (jobs.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Download activity", fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
                            if (jobs.any { it.state == "Queued" }) TextButton(onClick = { startQueue() }) { Text("Resume") }
                            if (jobs.any { it.state in setOf("Completed", "Failed", "Cancelled") }) TextButton(onClick = { DownloadQueue.clearFinished() }) { Text("Clear") }
                        }
                    }
                    items(jobs.takeLast(60).reversed(), key = { it.id }) { job ->
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PreviewImage(ytId(job.url), Modifier.size(width=89.dp,height=55.dp))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(job.title, maxLines=2, overflow=TextOverflow.Ellipsis, fontSize=13.sp, fontWeight=FontWeight.Medium)
                                    Text("${job.state} • ${job.quality} • ${job.progress.toInt()}%", color = if (job.state == "Completed") Cyan else Subtle, fontSize=11.sp)
                                }
                            }
                            if (job.state == "Downloading") LinearProgressIndicator(progress = { (job.progress/100f).coerceIn(0f,1f) }, modifier=Modifier.fillMaxWidth())
                            if (job.message.isNotBlank()) Text(job.message, color=Subtle,fontSize=11.sp)
                            Row {
                                if (job.state in setOf("Queued", "Downloading")) TextButton(onClick = {
                                    context.startService(Intent(context, DownloadService::class.java).setAction(DownloadService.CANCEL).putExtra(DownloadService.ID, job.id))
                                }) { Text("Cancel") }
                                if (job.state in setOf("Failed", "Cancelled", "Interrupted")) TextButton(onClick = {
                                    DownloadQueue.retry(job.id); startQueue()
                                }) { Text("Retry") }
                            }
                        }
                    }
                }
                item {
                    Text("ClipPort 0.3 beta • Saved to Downloads/ClipPort", color=Subtle,fontSize=11.sp)
                    Text("Download only media you own or have permission to save.", color=Subtle,fontSize=11.sp)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
