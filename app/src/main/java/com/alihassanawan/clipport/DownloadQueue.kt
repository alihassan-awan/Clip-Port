package com.alihassanawan.clipport

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val quality: String,
    val title: String,
    val thumbnail: String? = null,
    val state: String = "Queued",
    val progress: Float = 0f,
    val message: String = ""
)

/** Phase 2c: persistent queue metadata. Not a resumable partial-media implementation. */
object DownloadQueue {
    private const val PREFS = "clipport_queue_v1"
    private const val KEY = "items"
    private const val TAG = "ClipPortQueue"
    private val mutable = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items = mutable.asStateFlow()
    private var context: Context? = null
    private val lastPersistedProgress = mutableMapOf<String, Int>()

    @Synchronized
    fun initialize(app: Context) {
        if (context != null) return
        val ctx = app.applicationContext
        val json = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val restored = mutableListOf<DownloadItem>()
        try {
            val entries = JSONArray(json)
            for (i in 0 until entries.length()) {
                val value = entries.optJSONObject(i) ?: continue
                val id = value.optString("id")
                val url = value.optString("url")
                if (id.isBlank() || !Downloader.isSupported(url)) continue
                val oldState = value.optString("state", "Queued")
                // An app process that dies cannot guarantee its former worker still exists.
                val state = if (oldState == "Downloading") "Interrupted" else oldState
                restored.add(DownloadItem(
                    id = id,
                    url = url,
                    quality = value.optString("quality", "Best"),
                    title = value.optString("title", "Video"),
                    thumbnail = value.optString("thumbnail").takeIf { it.startsWith("https://") },
                    state = state,
                    progress = if (state == "Interrupted") 0f else value.optDouble("progress", 0.0).toFloat().coerceIn(0f,100f),
                    message = if (state == "Interrupted") "App stopped during download. Tap Retry to download again." else value.optString("message", "")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not restore queue", e)
        }
        context = ctx
        mutable.value = restored
        // Persist recovered states so the same stale Downloading status is not shown again.
        persist()
    }

    /** Must run only after initialize() in ClipPortApp.onCreate(). */
    @Synchronized
    fun enqueue(jobs: List<DownloadItem>) {
        check(context != null) { "Queue was not initialized" }
        mutable.value = mutable.value + jobs
        persist()
    }

    @Synchronized
    fun update(id: String, change: (DownloadItem) -> DownloadItem) {
        val previous = mutable.value.firstOrNull { it.id == id } ?: return
        val changed = change(previous)
        if (changed == previous) return
        mutable.value = mutable.value.map { if (it.id == id) changed else it }
        // Do not write flash storage for every download progress callback.
        val checkpoint = changed.progress.toInt() / 5
        val shouldPersist = changed.state != previous.state ||
            changed.message != previous.message ||
            (changed.state == "Downloading" && checkpoint != lastPersistedProgress[id])
        if (shouldPersist) {
            lastPersistedProgress[id] = checkpoint
            persist()
        }
    }

    @Synchronized
    fun retry(id: String) {
        update(id) {
            if (it.state in setOf("Failed", "Cancelled", "Interrupted"))
                it.copy(state = "Queued", progress = 0f, message = "")
            else it
        }
    }

    @Synchronized
    fun clearFinished() {
        mutable.value = mutable.value.filterNot { it.state in setOf("Completed", "Failed", "Cancelled") }
        persist()
    }

    @Synchronized
    fun get(id: String) = mutable.value.firstOrNull { it.id == id }

    private fun persist() {
        val ctx = context ?: return
        val array = JSONArray()
        mutable.value.forEach { item ->
            array.put(JSONObject().apply {
                put("id", item.id)
                put("url", item.url)
                put("quality", item.quality)
                put("title", item.title)
                put("thumbnail", item.thumbnail ?: "")
                put("state", item.state)
                put("progress", item.progress)
                put("message", item.message)
            })
        }
        if (!ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, array.toString()).commit()) {
            Log.e(TAG, "Queue persistence failed")
        }
    }
}
