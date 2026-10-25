package com.lilyai.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lilyai.ai.ConversationManager
import com.lilyai.ai.GroqClient
import com.lilyai.ai.Provider
import com.lilyai.ai.ProviderSettings
import com.lilyai.ai.ToolRegistry
import com.lilyai.ui.Palette
import com.lilyai.ui.dp
import com.lilyai.ui.heading
import com.lilyai.ui.input
import com.lilyai.ui.label
import com.lilyai.ui.pinkButton

// The chat window of the overlay. It sits at the top of the screen, so the keyboard does not cover it.
// It is the same chat as in the app: same keys, same memory of the conversation, same tools.
// [attachFocus] reads the text in the focus area (or the whole screen) so it can go with a message.
class OverlayChat(
    private val context: Context,
    private val windowManager: WindowManager,
    private val attachFocus: ((Result<String>) -> Unit) -> Unit
) {

    private val tools by lazy { ToolRegistry(context.applicationContext) }
    private val question = context.input("Ask LilyAi, or tell it what to do", multiline = true)
    private val attachedNote = context.label("")
    private val answer = TextView(context).apply {
        textSize = 14f
        setTextColor(Palette.plum)
    }
    private var attached: String? = null
    private var shown = false

    private fun rowButton(text: String, onClick: () -> Unit) = context.pinkButton(text, onClick).apply {
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(context.dp(3), 0, context.dp(3), 0) }
    }

    private val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = context.panelBackground()
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        addView(context.heading("Chat"))
        addView(question)
        addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(rowButton("Send") { send() })
                addView(rowButton("Attach focus") { attach() })
                addView(rowButton("Close") { hide() })
            }
        )
        addView(attachedNote)
        addView(
            ScrollView(context).apply { addView(answer) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(160))
        )
    }

    private val frame = FrameLayout(context).apply {
        setPadding(context.dp(12), 0, context.dp(12), 0)
        addView(card)
    }

    val isOpen: Boolean get() = shown

    fun show() {
        if (shown) return
        // Not marked as non-focusable, so the keyboard can type into it. Touches outside still reach the game.
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            y = context.dp(64)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
        }
        windowManager.addView(frame, params)
        shown = true
        question.requestFocus()
        frame.post {
            context.getSystemService(InputMethodManager::class.java)
                .showSoftInput(question, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    fun hide() {
        if (!shown) return
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(frame.windowToken, 0)
        windowManager.removeView(frame)
        shown = false
    }

    /** Hides the window without closing it, for example while a screenshot is taken. */
    fun setHidden(hidden: Boolean) {
        frame.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    private fun attach() {
        attachedNote.text = "Reading the screen..."
        attachFocus { result ->
            result.onSuccess { text ->
                if (text.isBlank()) {
                    attached = null
                    attachedNote.text = ""
                    toast("No text found")
                } else {
                    attached = text
                    attachedNote.text = "Attached: " + text.lines().size + " lines from the screen"
                }
            }.onFailure {
                attached = null
                attachedNote.text = ""
                toast("Failed: ${it.message}")
            }
        }
    }

    private fun send() {
        val text = question.text.toString().trim()
        if (text.isEmpty()) {
            toast("Type a message first")
            return
        }
        // Each message takes the next Groq key in turn, the same as in the app.
        val keys = ProviderSettings.takeTurn(context, Provider.GROQ)
        if (keys.isEmpty()) {
            toast("Add a Groq key in Secrets first")
            return
        }
        val extra = attached
        val prompt = if (extra == null) text else text + "\n\nText on screen (focus area):\n" + extra
        answer.text = "Thinking..."
        val model = ProviderSettings.model(context, Provider.GROQ)
        GroqClient.ask(keys, model, prompt, ConversationManager.history(), tools) { result ->
            result.onSuccess { reply ->
                ConversationManager.addExchange(prompt, reply)
                question.setText("")
                attached = null
                attachedNote.text = ""
            }
            answer.text = result.fold({ it }, { "Failed: ${it.message}" })
        }
    }

    private fun toast(message: String) =
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_LONG).show()
}
