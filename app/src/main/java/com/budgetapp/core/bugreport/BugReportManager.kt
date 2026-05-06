package com.budgetapp.core.bugreport

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.view.View
import com.budgetapp.BuildConfig
import com.budgetapp.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

private const val TAG = "BugReportManager"

data class BugReportResult(
    val success: Boolean,
    val issueUrl: String? = null,
    val error: String? = null
)

object BugReportManager {

    suspend fun captureScreenshot(view: View): Bitmap? = suspendCancellableCoroutine { cont ->
        try {
            if (view.width == 0 || view.height == 0) {
                AppLogger.w(TAG, "View has zero dimensions, skipping screenshot")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val window = (view.context as? Activity)?.window ?: run {
                AppLogger.w(TAG, "Context is not an Activity, cannot capture screenshot")
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            val location = IntArray(2)
            view.getLocationInWindow(location)
            val rect = Rect(
                location[0], location[1],
                location[0] + view.width, location[1] + view.height
            )
            PixelCopy.request(window, rect, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    AppLogger.d(TAG, "Screenshot captured ${bitmap.width}x${bitmap.height}")
                    cont.resume(bitmap)
                } else {
                    AppLogger.w(TAG, "PixelCopy returned error code $result")
                    cont.resume(null)
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Screenshot capture threw exception", e)
            cont.resume(null)
        }
    }

    suspend fun submitBugReport(
        title: String,
        description: String,
        labels: List<String>,
        screenshot: Bitmap?,
        deviceInfo: String,
        logs: String
    ): BugReportResult = withContext(Dispatchers.IO) {
        val token = BuildConfig.GITHUB_TOKEN
        val owner = BuildConfig.GITHUB_OWNER
        val repo = BuildConfig.GITHUB_REPO

        if (token.isBlank() || owner.isBlank() || repo.isBlank()) {
            val msg = "GitHub not configured — add github.token, github.owner, github.repo to local.properties"
            AppLogger.w(TAG, msg)
            return@withContext BugReportResult(false, error = msg)
        }

        return@withContext try {
            AppLogger.i(TAG, "Submitting bug report: \"$title\"")
            val screenshotUrl = screenshot?.let {
                runCatching { uploadScreenshot(it, token, owner, repo) }
                    .onFailure { e -> AppLogger.e(TAG, "Screenshot upload failed, continuing without it", e) }
                    .getOrNull()
            }
            val body = buildIssueBody(description, deviceInfo, logs, screenshotUrl)
            val allLabels = (listOf("bug") + labels).distinct()
            val issueUrl = createIssue(title, body, allLabels, token, owner, repo)
            AppLogger.i(TAG, "Issue created: $issueUrl")
            BugReportResult(success = true, issueUrl = issueUrl)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Bug report submission failed", e)
            BugReportResult(success = false, error = e.message ?: "Submission failed")
        }
    }

    private fun buildIssueBody(
        description: String,
        deviceInfo: String,
        logs: String,
        screenshotUrl: String?
    ) = buildString {
        appendLine("## Description")
        appendLine(description.ifBlank { "_No description provided_" })
        appendLine()
        appendLine("## Device Info")
        appendLine("```")
        append(deviceInfo)
        appendLine("```")
        if (screenshotUrl != null) {
            appendLine()
            appendLine("## Screenshot")
            appendLine("![Bug Screenshot]($screenshotUrl)")
        }
        appendLine()
        appendLine("## App Logs")
        appendLine("<details>")
        appendLine("<summary>Click to expand</summary>")
        appendLine()
        appendLine("```")
        appendLine(logs.takeLast(8000))
        appendLine("```")
        appendLine("</details>")
    }

    private fun uploadScreenshot(bitmap: Bitmap, token: String, owner: String, repo: String): String? {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val path = "bug-reports/screenshot_$timestamp.png"

        val payload = JSONObject().apply {
            put("message", "Bug report screenshot $timestamp")
            put("content", base64)
        }

        val conn = openConnection("PUT", "https://api.github.com/repos/$owner/$repo/contents/$path", token)
        conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code !in 200..201) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            AppLogger.w(TAG, "Screenshot upload HTTP $code: $err")
            return null
        }
        val response = JSONObject(conn.inputStream.bufferedReader().readText())
        return response.getJSONObject("content").getString("download_url")
    }

    private fun createIssue(
        title: String,
        body: String,
        labels: List<String>,
        token: String,
        owner: String,
        repo: String
    ): String {
        val payload = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("labels", JSONArray(labels))
        }

        val conn = openConnection("POST", "https://api.github.com/repos/$owner/$repo/issues", token)
        conn.outputStream.bufferedWriter().use { it.write(payload.toString()) }

        val code = conn.responseCode
        if (code != 201) {
            val err = conn.errorStream?.bufferedReader()?.readText()
            throw Exception("GitHub API HTTP $code: $err")
        }
        return JSONObject(conn.inputStream.bufferedReader().readText()).getString("html_url")
    }

    private fun openConnection(method: String, urlStr: String, token: String): HttpURLConnection {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "token $token")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.doOutput = true
        conn.connectTimeout = 20_000
        conn.readTimeout = 20_000
        return conn
    }

    fun getDeviceInfo(versionName: String) = buildString {
        appendLine("Device:    ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Android:   ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("App:       $versionName (${BuildConfig.VERSION_CODE})")
        append("Build:     ${Build.DISPLAY}")
    }
}
