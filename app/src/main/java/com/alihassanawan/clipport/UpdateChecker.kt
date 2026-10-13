package com.alihassanawan.clipport

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ClipPortRelease(val tag: String, val description: String, val link: String)

/** GitHub Releases discovery; APK installation remains an Android user-approved action. */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/alihassan-awan/Clip-Port/releases/latest"
    private const val RELEASES = "https://github.com/alihassan-awan/Clip-Port/releases"

    private fun parts(input: String): List<Int> = input.trim().removePrefix("v")
        .substringBefore('-').split('.').take(3).map { it.toIntOrNull() ?: 0 }

    private fun isNewer(latest: String, current: String): Boolean {
        val a = parts(latest); val b = parts(current)
        for (i in 0..2) {
            val av = a.getOrElse(i) { 0 }; val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av > bv
        }
        return false
    }

    suspend fun check(context: Context): ClipPortRelease? = withContext(Dispatchers.IO) {
        val connection = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ClipPort-Android")
        }
        try {
            if (connection.responseCode == 404) return@withContext null
            if (connection.responseCode !in 200..299) error("Update check unavailable (HTTP ${connection.responseCode})")
            val obj = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val tag = obj.optString("tag_name")
            val current = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "0.0.0"
            if (tag.isBlank() || !isNewer(tag, current)) null
            else ClipPortRelease(tag, obj.optString("name", "ClipPort update"),
                obj.optString("html_url").takeIf { value ->
                    try {
                        val u = Uri.parse(value)
                        u.scheme == "https" && u.host == "github.com" &&
                            u.encodedAuthority == "github.com" &&
                            (u.path ?: "").startsWith("/alihassan-awan/Clip-Port/releases/")
                    } catch (_: Exception) { false }
                } ?: RELEASES)
        } finally { connection.disconnect() }
    }

    fun openRelease(context: Context, release: ClipPortRelease) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(release.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
