package com.notimorph.app.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Checks GitHub Releases and downloads update APKs with live progress. */
object Updater {

    const val REPO_OWNER = "Shishir-ip"
    const val REPO_NAME = "NotiMorph"
    const val REPO_URL = "https://github.com/$REPO_OWNER/$REPO_NAME"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
        val htmlUrl: String,
    )

    suspend fun latest(): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "NotiMorph-App")
                .build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (resp.code == 404) throw IOException("No releases published yet.")
                if (!resp.isSuccessful) throw IOException("GitHub error HTTP ${resp.code}")
                val root = JSONObject(body)
                val assets = root.optJSONArray("assets")
                var apkUrl = ""
                var size = 0L
                if (assets != null) {
                    for (i in 0 until assets.length()) {
                        val a = assets.optJSONObject(i) ?: continue
                        val name = a.optString("name", "")
                        if (name.endsWith(".apk", ignoreCase = true)) {
                            apkUrl = a.optString("browser_download_url", "")
                            size = a.optLong("size", 0L)
                            break
                        }
                    }
                }
                if (apkUrl.isBlank()) throw IOException("Release has no APK asset.")
                Release(
                    version = root.optString("tag_name", "").removePrefix("v"),
                    notes = root.optString("body", "").ifBlank { "No release notes." },
                    apkUrl = apkUrl,
                    sizeBytes = size,
                    htmlUrl = root.optString("html_url", REPO_URL),
                )
            }
        }
    }

    /** Semantic-ish comparison: true when [remote] is strictly newer than [local]. */
    fun isNewer(remote: String, local: String): Boolean {
        fun nums(v: String) = v.removePrefix("v").removePrefix("V")
            .split(".", "-", "+").filter { it.isNotEmpty() }
            .map { it.takeWhile { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val a = nums(remote)
        val b = nums(local)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Streams the APK to [dest] reporting progress 0..100. */
    suspend fun download(
        url: String,
        dest: File,
        onProgress: (Int, Long, Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val part = File(dest.parentFile, dest.name + ".part")
            val request = Request.Builder().url(url).header("User-Agent", "NotiMorph-App").build()
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Download failed: HTTP ${resp.code}")
                val body = resp.body ?: throw IOException("Empty download body.")
                val total = body.contentLength()
                var read = 0L
                var lastPct = -1
                body.byteStream().use { input ->
                    part.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) {
                                val pct = ((read * 100) / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    onProgress(pct, read, total)
                                }
                            }
                        }
                    }
                }
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                onProgress(100, read, read)
                dest
            }
        }
    }
}
