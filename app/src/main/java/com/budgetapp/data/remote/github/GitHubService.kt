package com.budgetapp.data.remote.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubService @Inject constructor() {

    suspend fun createIssue(
        token: String,
        owner: String,
        repo: String,
        title: String,
        body: String
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/$owner/$repo/issues")
            val connection = url.openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $token")
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                connection.doOutput = true
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000

                val payload = JSONObject().apply {
                    put("title", title)
                    put("body", body)
                    put("labels", JSONArray().put("bug"))
                }.toString()

                connection.outputStream.use { it.write(payload.toByteArray()) }

                val responseCode = connection.responseCode
                if (responseCode != 201) {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                    val message = runCatching { JSONObject(errorBody).getString("message") }
                        .getOrDefault("HTTP $responseCode")
                    throw Exception(message)
                }

                val responseBody = connection.inputStream.bufferedReader().readText()
                JSONObject(responseBody).getString("html_url")
            } finally {
                connection.disconnect()
            }
        }
    }
}
