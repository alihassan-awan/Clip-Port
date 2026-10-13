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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Navy = Color(0xFF0C1225)
private val Panel = Color(0xFF19233A)
private val Accent = Color(0xFF8B87FC)
private val Txt = Color(0xFFF3F4FC)
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
    val manager = remember { Downloader(context) }
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(initialUrl) }
    var title by remember { mutableStateOf("") }
    var quality by remember { mutableStateOf("Best") }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Paste a video link to begin") }
    val colors = darkColorScheme(primary = Accent, background = Navy, surface = Panel, onBackground = Txt, onSurface = Txt)
    MaterialTheme(colorScheme = colors) {
        Surface(modifier = Modifier.fillMaxSize(), color = Navy) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Spacer(Modifier.height(8.dp))
                Text("◉ ClipPort", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, color = Txt)
                Text("Your videos. Your device.", fontSize = 15.sp, color = Muted)
                Spacer(Modifier.height(10.dp))
                Text("Save videos easily", fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = Txt)
                Text("Paste a link, choose quality and download directly to your phone — no account or hosting needed.", color = Muted, lineHeight = 22.sp)
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("Video link", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(value = url, onValueChange = { url = it; title = "" }, modifier = Modifier.fillMaxWidth(), label = { Text("https://youtube.com/watch?v=...") }, singleLine = true)
                        Button(onClick = {
                            if (!Downloader.isSupported(url)) { status = "Enter a valid HTTPS YouTube URL"; return@Button }
                            busy = true; status = "Analyzing video..."; title = ""
                            scope.launch {
                                try { title = manager.analyze(url); status = "Video ready" }
                                catch (e: Exception) { status = "Analysis failed: ${e.message ?: "Unknown error"}" }
                                finally { busy = false }
                            }
                        }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Analyze video") }
                    }
                }
                if (title.isNotBlank()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("Download format", color = Muted)
                            Box {
                                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(quality + "  ▾") }
                                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                    qualityOptions.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { quality = option; expanded = false }) }
                                }
                            }
                            Button(onClick = {
                                busy = true; progress = 0f; status = "Starting download..."
                                scope.launch {
                                    try {
                                        status = manager.download(url, quality) { value -> progress = value }
                                    } catch (e: Exception) { status = "Download failed: ${e.message ?: "Unknown error"}" }
                                    finally { busy = false }
                                }
                            }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) { Text("Download to phone", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
                if (busy) { LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()); Text("${progress.toInt()}%", color = Muted) }
                Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
                    Text(status, modifier = Modifier.fillMaxWidth().padding(16.dp), color = Txt)
                }
                Text("PHASE 1 · Single-video downloads", fontSize = 11.sp, color = Muted)
                Text("Use only for media you own or are authorized to download.", fontSize = 12.sp, color = Muted)
                Spacer(Modifier.height(15.dp))
                Text("Built by Ali Hassan Awan", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = Muted, fontSize = 13.sp)
            }
        }
    }
}
