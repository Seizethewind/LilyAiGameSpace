package com.lilyai.ai

// A service LilyAi can talk to. Every provider gets the same page in Secrets:
// its own list of API keys and a model name typed freely.
enum class Provider(
    val id: String,
    val displayName: String,
    val icon: String,
    val defaultModel: String
) {
    GROQ("groq", "Groq", "\u26A1", GroqClient.DEFAULT_MODEL)
}
