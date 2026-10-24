package com.lilyai.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lilyai.ai.GroqClient

// Where the Groq key and model name live. A saved key is encrypted, hidden, and cannot be
// edited until it is reset.
class SecretsScreen(private val activity: Activity) : Screen {

    override val title = "Secrets"

    private val keyArea = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val modelInput = activity.input("Groq model name")

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.heading("Groq API key"))
        addView(keyArea)
        addView(activity.heading("Groq model"))
        addView(activity.label("If Groq retires the model, type another name here. No rebuild needed."))
        addView(modelInput)
        addView(activity.pinkButton("Save model") {
            GroqClient.saveModel(activity, modelInput.text.toString())
            modelInput.setText(GroqClient.savedModel(activity))
            activity.toast("Model saved")
        })
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    override fun onShow() = render()

    private fun render() {
        keyArea.removeAllViews()
        if (GroqClient.savedKey(activity).isNotEmpty()) {
            keyArea.addView(
                activity.label(
                    "A key is saved and encrypted on this phone. It is hidden and cannot be edited. " +
                        "Reset it to enter a new one."
                )
            )
            keyArea.addView(activity.pinkButton("Reset key") { confirmReset() })
        } else {
            val keyInput = activity.input("Paste your Groq API key", password = true)
            keyArea.addView(keyInput)
            keyArea.addView(activity.pinkButton("Save key") { saveKey(keyInput.text.toString().trim()) })
        }
        modelInput.setText(GroqClient.savedModel(activity))
    }

    private fun saveKey(key: String) {
        if (key.isEmpty()) {
            activity.toast("Paste the key first")
            return
        }
        runCatching { GroqClient.saveKey(activity, key) }
            .onSuccess { render() }
            .onFailure { activity.toast("Could not encrypt the key on this phone: ${it.message}") }
    }

    private fun confirmReset() {
        AlertDialog.Builder(activity)
            .setTitle("Reset the saved key?")
            .setMessage("The key will be removed from this phone. You will need to paste it again.")
            .setPositiveButton("Reset") { _, _ ->
                GroqClient.saveKey(activity, "")
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
