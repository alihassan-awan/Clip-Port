package com.alihassanawan.clipport

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Navy = Color(0xFF0C1225)
private val Panel = Color(0xFF19233A)
private val Accent = Color(0xFF8B87FC)
private val Muted = Color(0xFFB3BDCC)
private val qualityOptions = listOf("Best", "1080p", "720p", "480p", "360p", "Audio M4A")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val initial = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        setContent { ClipPortScreen(initial) }
    }
}

@Composable
private fun ClipPortScreen(initialUrl: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val downloader = remember { Downloader(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var videoTitle by remember { mutableStateOf("") }
    var playlist by remember { mutableStateOf<PlaylistDetails?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var quality by remember { mutableStateOf("Best") }
    var dropdown by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var currentProgress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Paste a YouTube video or playlist link") }
    var completed by remember { mutableIntStateOf(0) }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Navy, surface = Panel)) {
        Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                Text("◉ ClipPort", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Text("Video and playlist downloads · Phase 2a", color = Muted)
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it; videoTitle = ""; playlist = null; selected.clear() },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("YouTube link") },
                            singleLine = true
                        )
                        Button(
                            enabled = !busy,
                            onClick = {
                                busy = true
                                videoTitle = ""; playlist = null; selected.clear()
                                status = "Analyzing link..."
                                scope.launch {
                                    try {
                                        if (Downloader.isPlaylist(url)) {
                                            playlist = downloader.analyzePlaylist(url)
                                            selected.addAll(playlist!!.videos.map { it.id })
                                            status = "${playlist!!.videos.size} entries loaded (first 50 maximum)"
                                        } else {
                                            videoTitle = downloader.analyze(url)
                                            status = "Video ready"
                                        }
                                    } catch (e: Exception) {
                                        status = "Analysis failed: ${e.message ?: "Unknown error"}"
                                    } finally { busy = false }
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) { Text("Analyze video / playlist") }
                    }
                }

                if (videoTitle.isNotBlank() || playlist != null) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(playlist?.title ?: videoTitle, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Quality (actual availability depends on video)", color = Muted, fontSize = 12.sp)
                            Box {
                                OutlinedButton(onClick = { dropdown = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text("$quality ▾")
                                }
                                DropdownMenu(expanded = dropdown, onDismissRequest = { dropdown = false }) {
                                    qualityOptions.forEach { option ->
                                        DropdownMenuItem(text = { Text(option) }, onClick = { quality = option; dropdown = false })
                                    }
                                }
                            }
                            playlist?.let { details ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(enabled = !busy, onClick = {
                                        selected.clear(); selected.addAll(details.videos.map { it.id })
                                    }) { Text("Select all") }
                                    TextButton(enabled = !busy, onClick = { selected.clear() }) { Text("Deselect all") }
                                }
                                Text("Selected ${selected.size} / ${details.videos.size}", color = Muted)
                                details.videos.forEach { video ->
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        Checkbox(
                                            checked = video.id in selected,
                                            enabled = !busy,
                                            onCheckedChange = { checked ->
                                                if (checked && video.id !in selected) selected.add(video.id)
                                                if (!checked) selected.remove(video.id)
                                            }
                                        )
                                        Text(video.title, modifier = Modifier.weight(1f).padding(top = 10.dp), fontSize = 13.sp)
                                    }
                                }
                            }
                            Button(
                                enabled = !busy && (playlist == null || selected.isNotEmpty()),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                onClick = {
                                    busy = true; currentProgress = 0f; completed = 0
                                    val jobs = playlist?.videos?.filter { it.id in selected }?.map { it.url } ?: listOf(url)
                                    scope.launch {
                                        try {
                                            jobs.forEachIndexed { index, videoUrl ->
                                                status = "Downloading ${index + 1}/${jobs.size}..."
                                                val message = downloader.download(videoUrl, quality) { amount ->
                                                    scope.launch(Dispatchers.Main) { currentProgress = amount }
                                                }
                                                completed++
                                                status = message
                                            }
                                            status = "Complete: $completed of ${jobs.size} saved in Downloads/ClipPort"
                                        } catch (e: Exception) {
                                            status = "Stopped after $completed completed: ${e.message ?: "Unknown error"}"
                                        } finally { busy = false }
                                    }
                                }
                            ) { Text(if (playlist != null) "Download selected (${selected.size})" else "Download to phone") }
                        }
                    }
                }
                if (busy) {
                    LinearProgressIndicator(
                        progress = { (currentProgress / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Current video: ${currentProgress.toInt()}%", color = Muted)
                }
                Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
                    Text(status, modifier = Modifier.fillMaxWidth().padding(16.dp))
                }
                Text("Phase 2a: playlist queue runs while app is open. Background service and cancellation are planned for Phase 2b.", color = Muted, fontSize = 12.sp)
                Text("Use only for content you own or have permission to download.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text("Built by Ali Hassan Awan", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Muted, fontSize = 13.sp)
            }
        }
    }
}
