package com.lilyai.ui

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lilyai.ai.ConversationManager
import com.lilyai.ai.GroqClient
import com.lilyai.ai.Provider
import com.lilyai.ai.ProviderSettings
import com.lilyai.ai.ToolRegistry

// Talk to LilyAi. It remembers the last few messages, and it can launch games and run Auto.
class ChatScreen(private val activity: Activity) : Screen {

    override val title = "Chat"

    private val tools by lazy { ToolRegistry(activity.applicationContext) }
    private val question = activity.input(
        "Ask LilyAi, or tell it to launch a game or start Auto",
        multiline = true
    )
    private val answer = TextView(activity).apply {
        setTextIsSelectable(true)
        setTextColor(Palette.plum)
        textSize = 15f
    }

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(question)
        addView(activity.pinkButton("Ask LilyAi") { ask() })
        addView(activity.pinkButton("New chat") {
            ConversationManager.clear()
            answer.text = ""
        })
        addView(answer)
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    private fun ask() {
        val text = question.text.toString().trim()
        if (text.isEmpty()) {
            activity.toast("Type a question first")
            return
        }
        // Each question takes the next Groq key in turn (key 1, 2 ... last, then 1 again).
        val keys = ProviderSettings.takeTurn(activity, Provider.GROQ)
        if (keys.isEmpty()) {
            activity.toast("Add a Groq key in Secrets first")
            return
        }
        answer.text = "Thinking..."
        val model = ProviderSettings.model(activity, Provider.GROQ)
        GroqClient.ask(keys, model, text, ConversationManager.history(), tools) { result ->
            result.onSuccess { reply ->
                ConversationManager.addExchange(text, reply)
                question.setText("")
            }
            answer.text = result.fold({ it }, { "Failed: ${it.message}" })
        }
    }
}
