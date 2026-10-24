package com.lilyai.ai

data class ChatMessage(val role: String, val content: String)

// Keeps the recent back-and-forth so LilyAi can follow the conversation.
// It lives in memory only: it is cleared on New chat and when the app process ends.
object ConversationManager {

    private const val MAX_MESSAGES = 12 // 6 exchanges

    private val messages = mutableListOf<ChatMessage>()

    /** A copy of the saved messages, oldest first. */
    fun history(): List<ChatMessage> = messages.toList()

    /** Saves one question and its answer. Call only after a successful answer. */
    fun addExchange(user: String, assistant: String) {
        messages += ChatMessage("user", user)
        messages += ChatMessage("assistant", assistant)
        // Drop the oldest exchange so the history always starts with a question.
        while (messages.size > MAX_MESSAGES) {
            messages.removeAt(0)
            messages.removeAt(0)
        }
    }

    fun clear() = messages.clear()
}
