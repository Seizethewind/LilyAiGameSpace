package com.lilyai.ai

import android.content.Context
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
// The API key is kept in the app's private storage.
object GroqClient {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"
    private const val MODEL = "llama-3.3-70b-versatile"
    private const val SYSTEM_PROMPT =
        "You are LilyAi, a concise assistant inside a phone game helper app. " +
            "You can run tools to launch games and control Auto mode. " +
            "Use a tool only when the user asks for that action, then tell the user what happened."
    private const val MAX_TOOL_ROUNDS = 4
    private const val TOOL_TIMEOUT_SECONDS = 10L
    private const val PREFS = "ai"
    private const val KEY_API = "groq_key"

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun savedKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_API, "").orEmpty()

    fun saveKey(context: Context, key: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_API, key).apply()
    }

    /**
     * Sends one question after the earlier [history]. The model may call tools from [tools]
     * before it answers. onResult runs on the main thread with the reply or the error.
     */
    fun ask(
        apiKey: String,
        prompt: String,
        history: List<ChatMessage>,
        tools: ToolRegistry,
        onResult: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { request(apiKey, prompt, history, tools) }
            mainHandler.post { onResult(result) }
        }
    }

    private fun request(
        apiKey: String,
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
            val reply = send(apiKey, messages, tools)
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

    private fun send(apiKey: String, messages: JSONArray, tools: ToolRegistry): JSONObject {
        val body = JSONObject()
            .put("model", MODEL)
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
