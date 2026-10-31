package com.alihassanawan.clipport

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

private val Night = Color(0xFF080C17)
private val Panel = Color(0xFF141C2F)
private val Soft = Color(0xFF202943)
private val Violet = Color(0xFFB3A2FF)
private val Cyan = Color(0xFF75E0DC)
private val Pale = Color(0xFFF4F5FF)
private val Subtle = Color(0xFFA9B6CD)
private val choices = listOf("720p", "Best", "1080p", "480p", "360p", "Audio M4A")

/** Resolve the VIDEO link from multi-URL share captions; never choose TikTok Lite ads. */
internal object SharedLinkParser {
    private val urls = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    private fun clean(raw: String): String = raw.trim().trimEnd('.', ',', '!', ')', ']', '}', ';', ':', '…', '،')

    private fun isMediaLink(candidate: String): Boolean {
        if (!Downloader.isSupported(candidate)) return false
        val uri = try { URI(candidate) } catch (_: Exception) { return false }
        val host = uri.host.orEmpty().lowercase().trimEnd('.')
        val path = uri.path.orEmpty().trim('/')
        if (host in setOf("vm.tiktok.com", "vt.tiktok.com")) {
            return path.isNotBlank() && !path.equals("tiktoklite", ignoreCase = true)
        }
        if (host.endsWith("tiktok.com")) {
            return Regex("^@[^/]+/video/[0-9]+(?:/.*)?$").matches(path) ||
                Regex("^t/[A-Za-z0-9]+/?$").matches(path) ||
                Regex("^v/[0-9]+/?$").matches(path)
        }
        return true
    }

    fun extract(text: String): String? {
        val matches = urls.findAll(text)
            .map { clean(it.value) }
            .filter { isMediaLink(it) }
            .toList()
        // A TikTok post can contain both a video URL and a TikTok Lite install URL.
        // The install URL must never be treated as media even when it is the last link.
        return matches.firstOrNull()
    }

    fun fromUserInput(text: String): String {
        // Preserve ordinary editing, but clean up pasted share-caption bundles.
        val trimmed = text.trim()
        return if (trimmed.contains('\n') || Regex("https?://", RegexOption.IGNORE_CASE).findAll(trimmed).count() > 1 ||
            (trimmed.contains(' ') && trimmed.contains("http", ignoreCase = true))) {
            extract(trimmed) ?: trimmed
        } else trimmed
    }
}

private fun friendlyAnalysisError(e: Exception): String {
    val message = e.message.orEmpty()
    return when {
        message.contains("IP address is blocked", ignoreCase = true) ->
            "TikTok is refusing this network/IP for this post. ClipPort cannot override that restriction. Try a public post you can open normally in your browser."
        message.contains("impersonation", ignoreCase = true) ->
            "TikTok requires browser impersonation not available in this Android yt-dlp build. Try another accessible video or update the downloader engine."
        message.contains("403") ->
            "The platform rejected the media request (HTTP 403). Try a different public post or retry later."
        message.contains("Use a public HTTPS", ignoreCase = true) ->
            "No valid video URL found. From TikTok Lite, use Share → Copy link, not the promotional TikTok Lite install link."
        else -> "Analysis failed: ${message.ifBlank { "Unknown error" }.take(240)}"
    }
}

class MainActivity : ComponentActivity() {
    private var latestSharedLink by mutableStateOf("")
    private var invalidShare by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = if (intent?.action == Intent.ACTION_SEND) {
            SharedLinkParser.extract(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()).orEmpty()
        } else ""
        latestSharedLink = initial
        invalidShare = intent?.action == Intent.ACTION_SEND && initial.isBlank()
        setContent { PremiumScreen(latestSharedLink, invalidShare) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_SEND) {
            val cleaned = SharedLinkParser.extract(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            invalidShare = cleaned == null
            if (cleaned != null) latestSharedLink = cleaned
        }
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
private fun PreviewImage(videoId: String?, modifier: Modifier = Modifier, imageUrl: String? = null) {
    Box(modifier = modifier.background(Soft, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        if (imageUrl != null || videoId != null) AsyncImage(
            model = imageUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
            contentDescription = "Video thumbnail",
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        ) else Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Cyan)
    }
}

@Composable
private fun PremiumScreen(initialUrl: String, invalidSharedLink: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { Downloader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val jobs by DownloadQueue.items.collectAsState()
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var currentTab by remember { mutableStateOf("Discover") }
    var pendingShared by remember { mutableStateOf(initialUrl.isNotBlank()) }
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf("") }
    var mediaThumbnail by remember { mutableStateOf<String?>(null) }
    var mediaSource by remember { mutableStateOf("YouTube") }
    var playlist by remember { mutableStateOf<PlaylistDetails?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var quality by remember { mutableStateOf("720p") }
    var qualityMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(
        if (invalidSharedLink) "No video URL in this shared text. In TikTok Lite use Share → Copy link, not the TikTok Lite installation link."
        else "Paste a video or playlist link to get started."
    ) }
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
    LaunchedEffect(invalidSharedLink) {
        if (invalidSharedLink) {
            currentTab = "Discover"
            status = "No video URL in this shared text. In TikTok Lite use Share → Copy link, not the TikTok Lite installation link."
        }
    }
    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && initialUrl != url) {
            url = initialUrl
            pendingShared = true
            currentTab = "Discover"
        }
    }
    // Share intent initiates analysis once; no automatic download starts.
    LaunchedEffect(pendingShared, url) {
        if (pendingShared && url.isNotBlank() && !busy) {
            pendingShared = false
            busy = true
            status = "Analyzing shared link..."
            title = ""; playlist = null; selected.clear()
            try {
                if (Downloader.isPlaylist(url)) {
                    val p = downloader.analyzePlaylist(url)
                    playlist = p; selected.addAll(p.videos.map { it.id })
                    status = "${p.videos.size} playlist videos ready"
                } else {
                    val result = downloader.analyzeMedia(url)
                    title = result.title; mediaThumbnail = result.thumbnail
                    mediaSource = result.source
                    status = "${result.source} video ready"
                }
            } catch (e: Exception) {
                status = friendlyAnalysisError(e)
            } finally { busy = false }
        }
    }
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
                    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(20.dp)).padding(5.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Discover", "Downloads").forEach { tab ->
                            val active = currentTab == tab
                            Surface(
                                modifier = Modifier.weight(1f).clickable { currentTab = tab },
                                shape = RoundedCornerShape(16.dp),
                                color = if (active) Violet else Color.Transparent
                            ) {
                                Row(Modifier.padding(vertical = 13.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (tab == "Discover") Icons.Default.Explore else Icons.Default.Downloading,
                                        contentDescription = null, modifier = Modifier.size(18.dp),
                                        tint = if (active) Night else Subtle)
                                    Spacer(Modifier.width(7.dp))
                                    Text(tab, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                        color = if (active) Night else Subtle)
                                    if (tab == "Downloads" && jobs.any { it.state in setOf("Queued", "Downloading") }) {
                                        Spacer(Modifier.width(5.dp)); Text("•", color = if (active) Night else Cyan)
                                    }
                                }
                            }
                        }
                    }
                }
                if (currentTab == "Discover") item {
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1D2851), Panel)), RoundedCornerShape(26.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("All your videos. One place.", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 33.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf("YouTube", "Facebook", "TikTok").forEach { platform ->
                                Surface(color = Soft, shape = RoundedCornerShape(50.dp)) {
                                    Text(platform, modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), color = if (platform == "YouTube") Violet else Cyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Text("Discover, select and save videos directly to your phone. No account or remote server required.", color = Subtle, fontSize = 13.sp)
                        OutlinedTextField(value = url, onValueChange = { url = SharedLinkParser.fromUserInput(it); title = ""; mediaThumbnail = null; playlist = null; selected.clear() },
                            modifier = Modifier.fillMaxWidth(), placeholder = { Text("Paste YouTube, Facebook, TikTok or Instagram URL") },
                            singleLine = true, shape = RoundedCornerShape(15.dp),
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) })
                        Button(onClick = {
                            busy = true; title = ""; playlist = null; selected.clear(); status = "Analyzing media..."
                            scope.launch {
                                try {
                                    if (!Downloader.isSupported(url)) error("Use a public HTTPS YouTube, Facebook, TikTok or Instagram link")
                                    if (Downloader.isPlaylist(url)) {
                                        val p = downloader.analyzePlaylist(url)
                                        playlist = p; selected.addAll(p.videos.map { it.id }); status = "${p.videos.size} videos found"
                                    } else {
                                        val result = downloader.analyzeMedia(url)
                                        title = result.title
                                        mediaThumbnail = result.thumbnail
                                        mediaSource = result.source
                                        status = "$mediaSource video ready"
                                    }
                                } catch (e: Exception) { status = friendlyAnalysisError(e) }
                                finally { busy = false }
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Search, contentDescription = null); Spacer(Modifier.width(9.dp)); Text(if (busy) "Analyzing..." else "Analyze link", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (currentTab == "Discover" && (playlist != null || title.isNotBlank())) item {
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
                        Text("MEDIA PREVIEW", color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                        if (playlist == null) {
                            PreviewImage(ytId(url), Modifier.fillMaxWidth().height(190.dp), mediaThumbnail)
                            Text(mediaSource.uppercase(), fontSize = 10.sp, color = Cyan, fontWeight = FontWeight.Bold)
                            Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(playlist!!.title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("${playlist!!.videos.size} videos · YouTube playlist", color = Subtle)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Download quality", color = Subtle, modifier = Modifier.weight(1f))
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
                            val toAdd = playlist?.videos?.filter { it.id in selected }?.map { DownloadItem(url=it.url, quality=quality, title=it.title, thumbnail="https://i.ytimg.com/vi/${it.id}/hqdefault.jpg") }
                                ?: listOf(DownloadItem(url=url, quality=quality, title=title, thumbnail=mediaThumbnail))
                            DownloadQueue.enqueue(toAdd)
                            startQueue()
                            status = "${toAdd.size} item(s) added to queue"
                            currentTab = "Downloads"
                        }, enabled = playlist == null || selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                            Icon(Icons.Default.Download, contentDescription = null); Spacer(Modifier.width(8.dp))
                            Text(if (playlist == null) "Download to phone" else "Download selected (${selected.size})")
                        }
                    }
                }
                if (currentTab == "Discover") item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Cyan)
                        Text(status, color = Subtle, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    }
                }
                if (currentTab == "Discover" && (update != null || updateMessage.isNotBlank())) item {
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(updateMessage, fontSize = 13.sp)
                        update?.let { release -> Button(onClick = { UpdateChecker.openRelease(context, release) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = null); Spacer(Modifier.width(7.dp)); Text("View verified GitHub Release")
                        } }
                    }
                }
                if (currentTab == "Statuses") {
                    item { WhatsAppStatusScreen() }
                }

                if (currentTab == "Downloads") {
                    item {
                        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF222D52), Panel)), RoundedCornerShape(24.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Your downloads", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                            Text("Track downloads, review results and retry when needed.", color = Subtle, fontSize = 12.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryTile("Active", jobs.count { it.state == "Downloading" || it.state == "Queued" }, Modifier.weight(1f))
                                SummaryTile("Saved", jobs.count { it.state == "Completed" }, Modifier.weight(1f))
                                SummaryTile("Failed", jobs.count { it.state == "Failed" }, Modifier.weight(1f))
                            }
                        }
                    }
                    if (jobs.isEmpty()) item {
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(24.dp)).padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Cyan, modifier = Modifier.size(48.dp))
                            Text("Nothing here yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Analyze a video or playlist to start downloading.", color = Subtle, fontSize = 12.sp)
                            OutlinedButton(onClick = { currentTab = "Discover" }) { Text("Find videos") }
                        }
                    }
                }
                if (currentTab == "Downloads" && jobs.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Activity", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            if (jobs.any { it.state == "Queued" }) TextButton(onClick = { startQueue() }) { Text("Resume") }
                            if (jobs.any { it.state in setOf("Completed", "Failed", "Cancelled") }) TextButton(onClick = { DownloadQueue.clearFinished() }) { Text("Clear") }
                        }
                    }
                    items(jobs.takeLast(60).reversed(), key = { it.id }) { job ->
                        Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PreviewImage(ytId(job.url), Modifier.size(width=89.dp,height=55.dp), job.thumbnail)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(job.title, maxLines=2, overflow=TextOverflow.Ellipsis, fontSize=13.sp, fontWeight=FontWeight.Medium)
                                    Text("${Downloader.source(job.url)}  ·  ${job.state}  ·  ${job.quality}  ·  ${job.progress.toInt()}%", color = if (job.state == "Completed") Cyan else Subtle, fontSize=11.sp)
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
                    Text("ClipPort v1.1.1 • Saved to Downloads/ClipPort", color=Subtle,fontSize=11.sp)
                    Text("Download only media you own or have permission to save.", color=Subtle,fontSize=11.sp)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun SummaryTile(label: String, count: Int, modifier: Modifier = Modifier) {
    Column(modifier.background(Soft, RoundedCornerShape(17.dp)).padding(vertical = 13.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(count.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Pale)
        Text(label, color = Subtle, fontSize = 11.sp)
    }
}
