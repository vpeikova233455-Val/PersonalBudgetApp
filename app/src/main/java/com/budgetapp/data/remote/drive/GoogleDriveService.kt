package com.budgetapp.data.remote.drive

import com.budgetapp.core.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GoogleDriveService"
private const val BASE = "https://www.googleapis.com/drive/v3"
private const val UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
private const val FOLDER_MIME = "application/vnd.google-apps.folder"
private const val EXCEL_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private const val BOUNDARY = "budget_app_backup_boundary"

data class DriveFileInfo(val id: String, val name: String)

@Singleton
class GoogleDriveService @Inject constructor() {

    /** Returns the folder ID for the backup folder, creating it if needed. Caches nothing — caller caches. */
    suspend fun getOrCreateFolder(token: String, folderName: String): String = withContext(Dispatchers.IO) {
        val existing = findFile(token, "name='${folderName.escapeDriveQuery()}' and mimeType='$FOLDER_MIME' and trashed=false")
        if (existing != null) {
            AppLogger.d(TAG, "Backup folder already exists: ${existing.id}")
            return@withContext existing.id
        }
        val payload = JSONObject().apply {
            put("name", folderName)
            put("mimeType", FOLDER_MIME)
        }.toString()
        val conn = openConnection("POST", "$BASE/files", token)
        conn.outputStream.bufferedWriter().use { it.write(payload) }
        val code = conn.responseCode
        if (code !in 200..201) throw Exception("Create folder HTTP $code: ${conn.errorText()}")
        val id = JSONObject(conn.inputStream.bufferedReader().readText()).getString("id")
        AppLogger.i(TAG, "Created backup folder: $id")
        id
    }

    /** Uploads or replaces a file in the given folder. Returns the file ID. */
    suspend fun uploadOrReplaceFile(
        token: String,
        folderId: String,
        fileName: String,
        bytes: ByteArray
    ): String = withContext(Dispatchers.IO) {
        val existing = findFile(
            token,
            "name='${fileName.escapeDriveQuery()}' and '${folderId}' in parents and trashed=false"
        )
        return@withContext if (existing != null) {
            updateFileContent(token, existing.id, bytes)
        } else {
            createFile(token, folderId, fileName, bytes)
        }
    }

    private fun findFile(token: String, q: String): DriveFileInfo? {
        val encoded = URLEncoder.encode(q, "UTF-8")
        val conn = openConnection("GET", "$BASE/files?q=$encoded&fields=files(id,name)&pageSize=1", token, output = false)
        val code = conn.responseCode
        if (code != 200) return null
        val files = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONArray("files")
        return if (files.length() == 0) null
        else files.getJSONObject(0).let { DriveFileInfo(it.getString("id"), it.getString("name")) }
    }

    private fun createFile(token: String, folderId: String, name: String, bytes: ByteArray): String {
        val metadata = JSONObject().apply {
            put("name", name)
            put("parents", org.json.JSONArray().put(folderId))
        }.toString().toByteArray()

        val body = buildMultipart(metadata, bytes)
        val conn = openConnection(
            "POST",
            "$UPLOAD_BASE/files?uploadType=multipart&fields=id",
            token,
            contentType = "multipart/related; boundary=$BOUNDARY"
        )
        conn.outputStream.use { it.write(body) }
        val code = conn.responseCode
        if (code !in 200..201) throw Exception("Create file HTTP $code: ${conn.errorText()}")
        val id = JSONObject(conn.inputStream.bufferedReader().readText()).getString("id")
        AppLogger.d(TAG, "Uploaded new file '$name': $id")
        return id
    }

    private fun updateFileContent(token: String, fileId: String, bytes: ByteArray): String {
        val conn = openConnection(
            "PATCH",
            "$UPLOAD_BASE/files/$fileId?uploadType=media&fields=id",
            token,
            contentType = EXCEL_MIME
        )
        conn.outputStream.use { it.write(bytes) }
        val code = conn.responseCode
        if (code !in 200..201) throw Exception("Update file HTTP $code: ${conn.errorText()}")
        AppLogger.d(TAG, "Updated file: $fileId")
        return fileId
    }

    private fun buildMultipart(metadataBytes: ByteArray, contentBytes: ByteArray): ByteArray {
        val nl = "\r\n"
        val prefix = buildString {
            append("--$BOUNDARY$nl")
            append("Content-Type: application/json; charset=UTF-8$nl$nl")
        }.toByteArray()
        val mid = buildString {
            append("$nl--$BOUNDARY$nl")
            append("Content-Type: $EXCEL_MIME$nl$nl")
        }.toByteArray()
        val suffix = "$nl--$BOUNDARY--$nl".toByteArray()
        return prefix + metadataBytes + mid + contentBytes + suffix
    }

    private fun openConnection(
        method: String,
        url: String,
        token: String,
        contentType: String = "application/json",
        output: Boolean = true
    ): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("Content-Type", contentType)
        conn.doOutput = output
        conn.connectTimeout = 30_000
        conn.readTimeout = 30_000
        return conn
    }

    private fun HttpURLConnection.errorText() =
        errorStream?.bufferedReader()?.readText().orEmpty()

    private fun String.escapeDriveQuery() = replace("'", "\\'")
}
