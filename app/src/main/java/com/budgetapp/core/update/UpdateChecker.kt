package com.budgetapp.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.budgetapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val latestVersion: String,
    val downloadUrl: String,
    val releaseNotes: String
)

@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Fall back to hard-coded values so the checker works even if local.properties
    // was not configured before the build.
    private val owner = BuildConfig.GITHUB_OWNER.ifBlank { "vpeikova233455-Val" }
    private val repo  = BuildConfig.GITHUB_REPO.ifBlank  { "PersonalBudgetApp" }

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = openConnection("https://api.github.com/repos/$owner/$repo/releases/latest")
            if (conn.responseCode != 200) return@withContext null

            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name")          // e.g. "v1.6.0"
            val latestVersion = tag.trimStart('v')
            if (!isNewer(latestVersion, BuildConfig.VERSION_NAME)) return@withContext null

            val assets = json.getJSONArray("assets")
            val downloadUrl = (0 until assets.length())
                .map { assets.getJSONObject(it) }
                .firstOrNull { it.getString("name").endsWith(".apk") }
                ?.getString("browser_download_url")
                ?: return@withContext null

            UpdateInfo(
                latestVersion = latestVersion,
                downloadUrl = downloadUrl,
                releaseNotes = json.optString("body", "").take(500)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun downloadApk(
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.connect()

            val total = conn.contentLength.toLong()
            val file = File(context.cacheDir, "update.apk")

            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var downloaded = 0L
                    var n = input.read(buf)
                    while (n != -1) {
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) onProgress((downloaded * 100 / total).toInt())
                        n = input.read(buf)
                    }
                }
            }
            file
        } catch (_: Exception) {
            null
        }
    }

    fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.update_provider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun openConnection(urlStr: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        // Optional token for private repos or higher rate limits
        BuildConfig.GITHUB_TOKEN.takeIf { it.isNotBlank() }?.let {
            conn.setRequestProperty("Authorization", "token $it")
        }
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        return conn
    }

    // Returns true if `latest` is a higher semantic version than `current`.
    private fun isNewer(latest: String, current: String): Boolean {
        return try {
            val l = latest.split(".").map { it.toInt() }
            val c = current.split(".").map { it.toInt() }
            for (i in 0 until maxOf(l.size, c.size)) {
                val lv = l.getOrElse(i) { 0 }
                val cv = c.getOrElse(i) { 0 }
                if (lv != cv) return lv > cv
            }
            false
        } catch (_: Exception) {
            false
        }
    }
}
