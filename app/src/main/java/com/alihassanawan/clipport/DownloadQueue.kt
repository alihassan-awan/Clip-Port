package com.alihassanawan.clipport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class DownloadItem(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val quality: String,
    val title: String,
    val state: String = "Queued",
    val progress: Float = 0f,
    val message: String = ""
)

/** In-memory queue for one app process. Pending jobs are not persisted across reboot. */
object DownloadQueue {
    private val mutable = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items = mutable.asStateFlow()
    @Synchronized fun enqueue(jobs: List<DownloadItem>) {
        mutable.value = mutable.value + jobs
    }
    @Synchronized fun update(id: String, change: (DownloadItem) -> DownloadItem) {
        mutable.value = mutable.value.map { if (it.id == id) change(it) else it }
    }
    @Synchronized fun retry(id: String) {
        update(id) { if (it.state == "Failed" || it.state == "Cancelled") it.copy(state = "Queued",progress = 0f,message = "") else it }
    }
    fun get(id: String) = items.value.firstOrNull { it.id == id }
}
