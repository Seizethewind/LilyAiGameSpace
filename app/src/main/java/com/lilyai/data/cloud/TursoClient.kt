package com.lilyai.data.cloud

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// Talks to a Turso database over the web (Turso's "SQL over HTTP"): one POST with the SQL and
// the token. No library is needed.
object TursoClient {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private class TursoException(message: String) : Exception(message)

    /**
     * Runs one SQL statement. onResult runs on the main thread with the statement's result
     * (its "cols" and "rows"), or with the reason it failed.
     */
    fun execute(address: String, token: String, sql: String, onResult: (Result<JSONObject>) -> Unit) {
        executor.execute {
            val result = runCatching { run(address, token, sql) }
            mainHandler.post { onResult(result) }
        }
    }

    // Accepts libsql://name-org.turso.io or https://name-org.turso.io and returns the web address
    // that takes the SQL.
    private fun pipelineUrl(address: String): String {
        val trimmed = address.trim().trimEnd('/').removeSuffix("/v2/pipeline")
        val https = if (trimmed.startsWith("libsql://")) {
            "https://" + trimmed.removePrefix("libsql://")
        } else {
            trimmed
        }
        require(https.startsWith("https://")) {
            "The address should look like libsql://name-org.turso.io"
        }
        return "$https/v2/pipeline"
    }

    private fun run(address: String, token: String, sql: String): JSONObject {
        val requests = JSONArray()
            .put(JSONObject().put("type", "execute").put("stmt", JSONObject().put("sql", sql)))
            .put(JSONObject().put("type", "close"))
        val body = JSONObject().put("requests", requests)

        val connection = (URL(pipelineUrl(address)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 401 || code == 403) throw TursoException("Turso refused the token (code $code)")
            if (code !in 200..299) throw TursoException("Turso error $code: ${text.take(200)}")

            val first = JSONObject(text).getJSONArray("results").getJSONObject(0)
            if (first.optString("type") == "error") {
                throw TursoException(first.getJSONObject("error").optString("message", "The statement failed"))
            }
            return first.getJSONObject("response").getJSONObject("result")
        } finally {
            connection.disconnect()
        }
    }
}
