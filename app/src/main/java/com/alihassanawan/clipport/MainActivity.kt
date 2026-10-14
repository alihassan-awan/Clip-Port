package com.alihassanawan.clipport

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val Bg = Color(0xFF060913)
private val BgTop = Color(0xFF0D1222)
private val SurfaceA = Color(0xFF10182A)
private val SurfaceB = Color(0xFF161F35)
private val SurfaceC = Color(0xFF222E4B)
private val Violet = Color(0xFF9D8CFF)
private val Cyan = Color(0xFF69E3D3)
private val Blue = Color(0xFF5F8DFF)
private val Pale = Color(0xFFF4F7FF)
private val Muted = Color(0xFFAAB4C8)
private val SoftBorder = Color(0x22FFFFFF)
private val choices = listOf("Best", "1080p", "720p", "480p", "360p", "Audio M4A")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        } else ""
        setContent { ClipPortFinalScreen(initial) }
    }
}

private fun ytId(raw: String): String? = try {
    val uri = URI(raw)
    val host = uri.host.orEmpty().lowercase()
    val id = if (host == "youtu.be") {
        uri.path.trim('/')
    } else {
        uri.rawQuery.orEmpty().split('&')
            .firstOrNull { it.startsWith("v=") }
            ?.substringAfter('=')
    }
    id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
} catch (_: Exception) {
    null
}

@Composable
private fun PreviewImage(videoId: String?, modifier: Modifier = Modifier, imageUrl: String? = null) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceC),
        contentAlignment = Alignment.Center
    ) {
        if (imageUrl != null || videoId != null) {
            AsyncImage(
                model = imageUrl ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                contentDescription = "Video thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(38.dp)
            )
        }
    }
}

@Composable
private fun ClipPortFinalScreen(initialUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { Downloader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val jobs by DownloadQueue.items.collectAsState()
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    var currentTab by remember { mutableStateOf("Discover") }
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf("") }
    var mediaThumbnail by remember { mutableStateOf<String?>(null) }
    var mediaSource by remember { mutableStateOf("YouTube") }
    var playlist by remember { mutableStateOf<PlaylistDetails?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var quality by remember { mutableStateOf("Best") }
    var qualityMenu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Paste a public video or playlist link to begin.") }
    var update by remember { mutableStateOf<ClipPortRelease?>(null) }
    var updateMessage by remember { mutableStateOf("") }
    var checking by remember { mutableStateOf(false) }

    fun startQueue() {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        ContextCompat.startForegroundService(
            context,
            Intent(context, DownloadService::class.java).setAction(DownloadService.START)
        )
    }

    fun checkUpdate() {
        if (checking) return
        checking = true
        scope.launch {
            try {
                update = UpdateChecker.check(context)
                updateMessage = if (update == null) {
                    "You are on the latest GitHub release channel."
                } else {
                    "Update available: ${update!!.tag}"
                }
            } catch (e: Exception) {
                updateMessage = "Update check unavailable: ${e.message ?: "network error"}"
            } finally {
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { checkUpdate() }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Violet,
            secondary = Cyan,
            background = Bg,
            surface = SurfaceA,
            onSurface = Pale,
            onBackground = Pale
        )
    ) {
        Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(BgTop, Bg))),
                contentPadding = PaddingValues(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    HeaderCard(
                        queued = jobs.count { it.state == "Queued" || it.state == "Downloading" },
                        saved = jobs.count { it.state == "Completed" },
                        onCheckUpdate = { checkUpdate() }
                    )
                }

                item {
                    SegmentedTabs(currentTab = currentTab) { currentTab = it }
                }

                if (currentTab == "Discover") {
                    item {
                        DiscoverHeroCard()
                    }

                    item {
                        InputCard(
                            url = url,
                            onUrlChange = {
                                url = it
                                title = ""
                                mediaThumbnail = null
                                playlist = null
                                selected.clear()
                            },
                            busy = busy,
                            onAnalyze = {
                                busy = true
                                title = ""
                                playlist = null
                                selected.clear()
                                status = "Analyzing media..."
                                scope.launch {
                                    try {
                                        if (!Downloader.isSupported(url)) {
                                            error("Use a public HTTPS YouTube, Facebook or TikTok link")
                                        }
                                        if (Downloader.isPlaylist(url)) {
                                            val p = downloader.analyzePlaylist(url)
                                            playlist = p
                                            selected.addAll(p.videos.map { it.id })
                                            mediaSource = "YouTube"
                                            status = "${p.videos.size} playlist videos ready"
                                        } else {
                                            val result = downloader.analyzeMedia(url)
                                            title = result.title
                                            mediaThumbnail = result.thumbnail
                                            mediaSource = result.source
                                            status = "${result.source} video ready"
                                        }
                                    } catch (e: Exception) {
                                        status = "Analysis failed: ${e.message ?: "Unknown error"}"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        )
                    }

                    if (playlist != null || title.isNotBlank()) {
                        item {
                            PreviewCard(
                                playlist = playlist,
                                url = url,
                                title = title,
                                mediaSource = mediaSource,
                                mediaThumbnail = mediaThumbnail,
                                quality = quality,
                                onOpenQuality = { qualityMenu = true },
                                selectedCount = selected.size,
                                onSelectAll = { playlist?.let { p -> selected.clear(); selected.addAll(p.videos.map { it.id }) } },
                                onSelectNone = { selected.clear() },
                                selected = selected,
                                onToggleVideo = { id ->
                                    if (id in selected) selected.remove(id) else selected.add(id)
                                },
                                onDownload = {
                                    val items = playlist?.videos
                                        ?.filter { it.id in selected }
                                        ?.map {
                                            DownloadItem(
                                                url = it.url,
                                                quality = quality,
                                                title = it.title,
                                                thumbnail = "https://i.ytimg.com/vi/${it.id}/hqdefault.jpg"
                                            )
                                        }
                                        ?: listOf(
                                            DownloadItem(
                                                url = url,
                                                quality = quality,
                                                title = title,
                                                thumbnail = mediaThumbnail
                                            )
                                        )
                                    DownloadQueue.enqueue(items)
                                    startQueue()
                                    status = "${items.size} item(s) added to queue"
                                    currentTab = "Downloads"
                                }
                            )
                        }
                    }

                    item {
                        StatusCard(status = status)
                    }

                    item {
                        UpdateCard(
                            updateMessage = updateMessage,
                            update = update,
                            onOpen = { release -> UpdateChecker.openRelease(context, release) }
                        )
                    }
                }

                if (currentTab == "Downloads") {
                    item {
                        DownloadsOverviewCard(jobs = jobs)
                    }

                    if (jobs.isEmpty()) {
                        item {
                            EmptyDownloadsCard { currentTab = "Discover" }
                        }
                    } else {
                        item {
                            DownloadsHeader(
                                hasQueued = jobs.any { it.state == "Queued" },
                                hasFinished = jobs.any { it.state in setOf("Completed", "Failed", "Cancelled") },
                                onResume = { startQueue() },
                                onClear = { DownloadQueue.clearFinished() }
                            )
                        }
                        items(jobs.takeLast(60).reversed(), key = { it.id }) { job ->
                            DownloadJobCard(
                                job = job,
                                onCancel = {
                                    context.startService(
                                        Intent(context, DownloadService::class.java)
                                            .setAction(DownloadService.CANCEL)
                                            .putExtra(DownloadService.ID, job.id)
                                    )
                                },
                                onRetry = {
                                    DownloadQueue.retry(job.id)
                                    startQueue()
                                }
                            )
                        }
                    }
                }

                item {
                    FooterCard()
                }
            }

            DropdownMenu(expanded = qualityMenu, onDismissRequest = { qualityMenu = false }) {
                choices.forEach { q ->
                    DropdownMenuItem(
                        text = { Text(q) },
                        onClick = {
                            quality = q
                            qualityMenu = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(queued: Int, saved: Int, onCheckUpdate: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF111A31), Color(0xFF0E1424))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Brush.linearGradient(listOf(Violet, Blue)), RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ClipPort", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Built by Ali Hassan Awan", color = Muted, fontSize = 12.sp)
                }
                IconButton(onClick = onCheckUpdate) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = "Check updates", tint = Cyan)
                }
            }
            Text(
                "A polished downloader for YouTube, TikTok and Facebook — with background queue, thumbnails and phone-first design.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TinyMetricCard("In queue", queued.toString(), Modifier.weight(1f))
                TinyMetricCard("Saved", saved.toString(), Modifier.weight(1f))
                TinyMetricCard("Security", "On", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TinyMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceB,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, color = Pale, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SegmentedTabs(currentTab: String, onChange: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Discover" to Icons.Default.Explore, "Downloads" to Icons.Default.Downloading).forEach { (tab, icon) ->
                val active = currentTab == tab
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onChange(tab) },
                    shape = RoundedCornerShape(18.dp),
                    color = if (active) Color(0xFFF2F5FF) else Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 13.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(icon, contentDescription = null, tint = if (active) Bg else Muted, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tab, color = if (active) Bg else Muted, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverHeroCard() {
    Surface(
        shape = RoundedCornerShape(30.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF18254A), Color(0xFF11192F))))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Fast downloads, classy UI.", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp)
            Text(
                "Paste a public link, preview the video, choose quality and send it straight to your download queue.",
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlatformChip("YouTube", Violet)
                PlatformChip("Facebook", Cyan)
                PlatformChip("TikTok", Blue)
            }
        }
    }
}

@Composable
private fun PlatformChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = SurfaceB,
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun InputCard(url: String, onUrlChange: (String) -> Unit, busy: Boolean, onAnalyze: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel("Paste link")
            Text("Supported: public YouTube videos and playlists, Facebook videos and TikTok videos.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                placeholder = { Text("https://...") },
                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) }
            )
            Button(
                onClick = onAnalyze,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Bg)
            ) {
                Icon(Icons.Default.Search, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (busy) "Analyzing..." else "Analyze link", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PreviewCard(
    playlist: PlaylistDetails?,
    url: String,
    title: String,
    mediaSource: String,
    mediaThumbnail: String?,
    quality: String,
    onOpenQuality: () -> Unit,
    selectedCount: Int,
    onSelectAll: () -> Unit,
    onSelectNone: () -> Unit,
    selected: List<String>,
    onToggleVideo: (String) -> Unit,
    onDownload: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel(if (playlist == null) "Preview" else "Playlist preview")
            if (playlist == null) {
                PreviewImage(ytId(url), Modifier.fillMaxWidth().height(210.dp), mediaThumbnail)
                SourceBadge(mediaSource)
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, lineHeight = 24.sp)
            } else {
                Text(playlist.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("${playlist.videos.size} videos ready for queue", color = Muted, fontSize = 12.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Quality", color = Muted, fontSize = 12.sp)
                    Text(quality, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                OutlinedButton(onClick = onOpenQuality, shape = RoundedCornerShape(16.dp)) {
                    Text("Change")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }

            playlist?.let { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$selectedCount selected", color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = onSelectAll) { Text("All") }
                    TextButton(onClick = onSelectNone) { Text("None") }
                }
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    p.videos.forEach { video ->
                        PlaylistItemCard(
                            video = video,
                            checked = video.id in selected,
                            onToggle = { onToggleVideo(video.id) }
                        )
                    }
                }
            }

            Button(
                onClick = onDownload,
                enabled = playlist == null || selectedCount > 0,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Bg)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (playlist == null) "Start download" else "Download selected ($selectedCount)",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlaylistItemCard(video: PlaylistVideo, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = SurfaceB,
        border = BorderStroke(1.dp, if (checked) Violet.copy(alpha = 0.45f) else SoftBorder),
        modifier = Modifier.fillMaxWidth().clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
            Spacer(Modifier.width(6.dp))
            PreviewImage(video.id, Modifier.size(width = 104.dp, height = 64.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                video.title,
                modifier = Modifier.weight(1f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatusCard(status: String) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Cyan)
            Spacer(Modifier.width(10.dp))
            Text(status, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

@Composable
private fun UpdateCard(updateMessage: String, update: ClipPortRelease?, onOpen: (ClipPortRelease) -> Unit) {
    if (updateMessage.isBlank() && update == null) return
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel("App updates")
            Text(updateMessage, color = Muted, fontSize = 12.sp)
            update?.let {
                OutlinedButton(onClick = { onOpen(it) }, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open verified GitHub release")
                }
            }
        }
    }
}

@Composable
private fun DownloadsOverviewCard(jobs: List<DownloadItem>) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(Color(0xFF171E35), Color(0xFF10182A))))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Download center", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Monitor active jobs, review finished files and manage failed items in one clean place.", color = Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryTile("Active", jobs.count { it.state == "Queued" || it.state == "Downloading" }, Modifier.weight(1f))
                SummaryTile("Saved", jobs.count { it.state == "Completed" }, Modifier.weight(1f))
                SummaryTile("Failed", jobs.count { it.state == "Failed" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DownloadsHeader(hasQueued: Boolean, hasFinished: Boolean, onResume: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Queue activity", fontWeight = FontWeight.Bold, fontSize = 21.sp, modifier = Modifier.weight(1f))
        if (hasQueued) {
            TextButton(onClick = onResume) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Resume")
            }
        }
        if (hasFinished) {
            TextButton(onClick = onClear) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Clear")
            }
        }
    }
}

@Composable
private fun DownloadJobCard(job: DownloadItem, onCancel: () -> Unit, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, stateColor(job.state).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PreviewImage(ytId(job.url), Modifier.size(width = 96.dp, height = 62.dp), job.thumbnail)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MicroBadge(Downloader.source(job.url), stateColor(Downloader.source(job.url)))
                        MicroBadge(job.state, stateColor(job.state))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text("${job.quality} • ${job.progress.toInt()}%", color = Muted, fontSize = 11.sp)
                }
            }
            if (job.state == "Downloading") {
                LinearProgressIndicator(
                    progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (job.message.isNotBlank()) {
                Text(job.message, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.state in setOf("Queued", "Downloading")) {
                    OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Default.PauseCircle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Cancel")
                    }
                }
                if (job.state in setOf("Failed", "Cancelled", "Interrupted")) {
                    Button(
                        onClick = onRetry,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet, contentColor = Bg)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Retry")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDownloadsCard(onFindVideos: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = SurfaceA,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Cyan, modifier = Modifier.size(48.dp))
            Text("Nothing downloaded yet", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("Analyze a link to start building your queue.", color = Muted, fontSize = 12.sp)
            OutlinedButton(onClick = onFindVideos, shape = RoundedCornerShape(16.dp)) {
                Text("Find videos")
            }
        }
    }
}

@Composable
private fun FooterCard() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("ClipPort v1.0.0 • Saved to Downloads/ClipPort", color = Muted, fontSize = 11.sp)
        Text("Download only media you own or have permission to save.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
}

@Composable
private fun SourceBadge(source: String) {
    MicroBadge(source, stateColor(source))
}

@Composable
private fun MicroBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(50.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.30f))
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SummaryTile(label: String, count: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceB,
        border = BorderStroke(1.dp, SoftBorder)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 13.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(count.toString(), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Pale)
            Text(label, color = Muted, fontSize = 11.sp)
        }
    }
}

private fun stateColor(state: String): Color = when (state.lowercase()) {
    "youtube" -> Violet
    "facebook" -> Blue
    "tiktok" -> Cyan
    "completed", "saved" -> Cyan
    "failed" -> Color(0xFFFF8383)
    "cancelled", "interrupted" -> Color(0xFFFFC266)
    "queued", "downloading", "active" -> Violet
    else -> Muted
}
