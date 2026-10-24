package com.lilyai.ai

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Talks to Groq's OpenAI-compatible chat endpoint, including tool calls. No library needed.
// The API key is stored encrypted (see KeyVault). The model name is plain text.
object GroqClient {

    /** Used until the user types another model name. Groq retires models from time to time. */
    const val DEFAULT_MODEL = "llama-3.3-70b-versatile"

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val SYSTEM_PROMPT =
        "You are LilyAi, a concise assistant inside a phone game helper app. " +
            "You can run tools to launch games and control Auto mode. " +
            "Use a tool only when the user asks for that action, then tell the user what happened."
    private const val MAX_TOOL_ROUNDS = 4
    private const val TOOL_TIMEOUT_SECONDS = 10L
    private const val PREFS = "ai"

    // Older versions kept the key as plain text under this name. It is only read to move it.
    private const val KEY_API_PLAIN = "groq_key"
    private const val KEY_API_ENCRYPTED = "groq_key_enc"
    private const val KEY_MODEL = "groq_model"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    /** The saved key, or an empty text if there is none (or it can no longer be decrypted). */
    fun savedKey(context: Context): String {
        val prefs = prefs(context)
        prefs.getString(KEY_API_ENCRYPTED, null)?.let { return KeyVault.decrypt(it).orEmpty() }

        // First run after the update: encrypt the old plain-text key once, then remove it.
        val legacy = prefs.getString(KEY_API_PLAIN, "").orEmpty()
        if (legacy.isNotEmpty()) runCatching { saveKey(context, legacy) }
        return legacy
    }

    /** Saves the key encrypted. An empty text removes it. */
    fun saveKey(context: Context, key: String) {
        val editor = prefs(context).edit().remove(KEY_API_PLAIN)
        if (key.isBlank()) {
            editor.remove(KEY_API_ENCRYPTED)
        } else {
            editor.putString(KEY_API_ENCRYPTED, KeyVault.encrypt(key.trim()))
        }
        editor.apply()
    }

    fun savedModel(context: Context): String =
        prefs(context).getString(KEY_MODEL, DEFAULT_MODEL).orEmpty().ifBlank { DEFAULT_MODEL }

    fun saveModel(context: Context, model: String) {
        prefs(context).edit().putString(KEY_MODEL, model.trim().ifEmpty { DEFAULT_MODEL }).apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Sends one question after the earlier [history]. The model may call tools from [tools]
     * before it answers. onResult runs on the main thread with the reply or the error.
     */
    fun ask(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatMessage>,
        tools: ToolRegistry,
        onResult: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { request(apiKey, model, prompt, history, tools) }
            mainHandler.post { onResult(result) }
        }
    }

    private fun request(
        apiKey: String,
        model: String,
        prompt: String,
        history: List<ChatMessage>,
        tools: ToolRegistry
    ): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", SYSTEM_PROMPT))
        history.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.content))
        }
        messages.put(JSONObject().put("role", "user").put("content", prompt))

        repeat(MAX_TOOL_ROUNDS) {
            val reply = send(apiKey, model, messages, tools)
            val content = if (reply.isNull("content")) "" else reply.getString("content")
            val calls = reply.optJSONArray("tool_calls")
            if (calls == null || calls.length() == 0) return content.ifBlank { "(no answer)" }

            messages.put(
                JSONObject().put("role", "assistant").put("content", content).put("tool_calls", calls)
            )
            for (i in 0 until calls.length()) {
                val call = calls.getJSONObject(i)
                val function = call.getJSONObject("function")
                val result = runTool(tools, function.getString("name"), function.optString("arguments", "{}"))
                messages.put(
                    JSONObject()
                        .put("role", "tool")
                        .put("tool_call_id", call.getString("id"))
                        .put("content", result)
                )
            }
        }
        error("The assistant used too many tool steps")
    }

    // Tools touch Android (activities, services), so they run on the main thread.
    private fun runTool(tools: ToolRegistry, name: String, arguments: String): String {
        val future = CompletableFuture<String>()
        mainHandler.post {
            future.complete(
                runCatching { tools.execute(name, JSONObject(arguments.ifBlank { "{}" })) }
                    .getOrElse { "Error: ${it.message}" }
            )
        }
        return runCatching { future.get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            .getOrElse { "Error: the tool took too long" }
    }

    private fun send(apiKey: String, model: String, messages: JSONArray, tools: ToolRegistry): JSONObject {
        val body = JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("tools", tools.definitions())

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
        } finally {
            connection.disconnect()
        }
    }

    // Groq errors look like {"error": {"message": "..."}}. Fall back to the raw text.
    private fun errorMessage(text: String): String =
        runCatching { JSONObject(text).getJSONObject("error").getString("message") }
            .getOrElse { text.take(200) }
}
