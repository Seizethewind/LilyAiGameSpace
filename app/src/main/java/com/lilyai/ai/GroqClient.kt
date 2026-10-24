package com.lilyai.ai

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
// Keys and the model name come from ProviderSettings.
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

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // A failed request with the status code, so a rejected key can be told from other errors.
    private class GroqHttpException(val code: Int, message: String) : Exception(message)

    /**
     * Sends one question after the earlier [history]. The model may call tools from [tools]
     * before it answers. [keys] is the list from ProviderSettings.takeTurn: the first key is
     * used, and the next ones only if a key is rejected (401) or rate limited (429).
     * onResult runs on the main thread with the reply or the error.
     */
    fun ask(
        keys: List<String>,
        model: String,
        prompt: String,
        history: List<ChatMessage>,
        tools: ToolRegistry,
        onResult: (Result<String>) -> Unit
    ) {
        executor.execute {
            val result = runCatching { request(keys, model, prompt, history, tools) }
            mainHandler.post { onResult(result) }
        }
    }

    private fun request(
        keys: List<String>,
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

        // Once a key works, the later steps of this question keep using it.
        var keyIndex = 0
        repeat(MAX_TOOL_ROUNDS) {
            val (reply, usedIndex) = sendWithFailover(keys, keyIndex, model, messages, tools)
            keyIndex = usedIndex
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

    // Tries the key at [from], then the following ones if a key is rejected or rate limited.
    // Returns the reply and the position of the key that worked.
    private fun sendWithFailover(
        keys: List<String>,
        from: Int,
        model: String,
        messages: JSONArray,
        tools: ToolRegistry
    ): Pair<JSONObject, Int> {
        var lastError: GroqHttpException? = null
        for (offset in keys.indices) {
            val index = (from + offset) % keys.size
            try {
                return send(keys[index], model, messages, tools) to index
            } catch (e: GroqHttpException) {
                if (e.code != 401 && e.code != 429) throw e
                lastError = e
            }
        }
        throw if (keys.size > 1) {
            IllegalStateException("All ${keys.size} keys failed. Last error: ${lastError?.message}")
        } else {
            lastError ?: IllegalStateException("No API key available")
        }
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
            if (code !in 200..299) throw GroqHttpException(code, "Groq error $code: ${errorMessage(text)}")
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
