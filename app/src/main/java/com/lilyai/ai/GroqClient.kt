package com.lilyai.ai

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

// Talks to Groq's OpenAI-compatible chat endpoint. No library needed for one request type.
// The API key is kept in the app's private storage.
object GroqClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"
    private const val SYSTEM_PROMPT = "You are LilyAi, a concise assistant inside a phone game helper app."
    private const val PREFS = "ai"
    private const val KEY_API = "groq_key"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun savedKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, "").orEmpty()

    fun saveKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API, key).apply()
    }

    /** Sends one question. onResult runs on the main thread with the reply or the error. */
    fun ask(apiKey: String, prompt: String, onResult: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching { request(apiKey, prompt) }
            mainHandler.post { onResult(result) }
        }
    }

    private fun request(apiKey: String, prompt: String): String {
        val messages = JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
            .put(JSONObject().put("role", "user").put("content", prompt))
        val body = JSONObject().put("model", MODEL).put("messages", messages)

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Authorization", "Bearer $apiKey")
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Groq error $code: ${errorMessage(text)}")
            return JSONObject(text)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        } finally {
            connection.disconnect()
        }
    }

    // Groq errors look like {"error": {"message": "..."}}. Fall back to the raw text.
    private fun errorMessage(text: String): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }
            .getOrElse { text.take(200) }
}
