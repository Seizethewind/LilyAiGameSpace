package com.lilyai.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lilyai.ai.Provider
import com.lilyai.ai.ProviderSettings

// The page every provider shares: its API keys (add, remove) and a model name typed freely.
// Keys are encrypted, hidden and cannot be edited. Each call uses the next key in the list.
class ProviderScreen(
    private val activity: Activity,
    private val provider: Provider
) : Screen {

    override val title = provider.displayName

    private val keysArea = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val modelInput = activity.input("Model name, for example ${provider.defaultModel}")

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.heading("${provider.icon}  ${provider.displayName}"))
        addView(activity.heading("API keys"))
        addView(keysArea)
        addView(activity.heading("Model"))
        addView(activity.label("Type any model name. It is only checked when you use it."))
        addView(modelInput)
        addView(activity.pinkButton("Save model") {
            ProviderSettings.setModel(activity, provider, modelInput.text.toString())
            modelInput.setText(ProviderSettings.model(activity, provider))
            activity.toast("Model saved")
        })
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    override fun onShow() {
        renderKeys()
        modelInput.setText(ProviderSettings.model(activity, provider))
    }

    private fun renderKeys() {
        keysArea.removeAllViews()
        val keys = ProviderSettings.keys(activity, provider)
        val summary = if (keys.isEmpty()) {
            "No keys yet."
        } else {
            val next = ProviderSettings.nextIndex(activity, provider) + 1
            "${keys.size} saved and encrypted. The next call uses key $next, then the following ones in order."
        }
        keysArea.addView(activity.label(summary))
        keys.indices.forEach { keysArea.addView(keyRow(it)) }
        keysArea.addView(addSection())
        if (keys.isNotEmpty()) {
            keysArea.addView(activity.pinkButton("Remove all keys") { confirmRemoveAll() })
        }
    }

    private fun keyRow(index: Int) = LinearLayout(activity).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(
            TextView(activity).apply {
                text = "Key ${index + 1}"
                textSize = 16f
                setTextColor(Palette.plum)
            },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        addView(
            activity.pinkButton("Remove") { confirmRemove(index) }.apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )
    }

    // The Add new key button opens a hidden text box and a Save key button.
    private fun addSection(): LinearLayout {
        val input = activity.input("Paste the new key", password = true)
        val save = activity.pinkButton("Save key") { saveNewKey(input.text.toString().trim()) }
        input.visibility = View.GONE
        save.visibility = View.GONE
        val add = activity.pinkButton("Add new key") {}
        add.setOnClickListener {
            input.visibility = View.VISIBLE
            save.visibility = View.VISIBLE
            add.visibility = View.GONE
        }
        return LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(add)
            addView(input)
            addView(save)
        }
    }

    private fun saveNewKey(key: String) {
        if (key.isEmpty()) {
            activity.toast("Paste the key first")
            return
        }
        runCatching { ProviderSettings.addKey(activity, provider, key) }
            .onSuccess { added ->
                if (added) renderKeys() else activity.toast("That key is already saved")
            }
            .onFailure { activity.toast("Could not encrypt the key on this phone: ${it.message}") }
    }

    private fun confirmRemove(index: Int) {
        AlertDialog.Builder(activity)
            .setTitle("Remove key ${index + 1}?")
            .setMessage("It will be removed from this phone. The keys after it move up one place.")
            .setPositiveButton("Remove") { _, _ ->
                ProviderSettings.removeKeyAt(activity, provider, index)
                renderKeys()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmRemoveAll() {
        AlertDialog.Builder(activity)
            .setTitle("Remove all keys?")
            .setMessage("Every ${provider.displayName} key will be removed from this phone.")
            .setPositiveButton("Remove all") { _, _ ->
                ProviderSettings.removeAllKeys(activity, provider)
                renderKeys()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
