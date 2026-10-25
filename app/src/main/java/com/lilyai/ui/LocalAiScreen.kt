package com.lilyai.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lilyai.ai.LocalModel
import com.lilyai.ai.LocalModels
import com.lilyai.ai.ModelDownloads
import com.lilyai.ai.ModelDownloads.State

// Your own AI: pick a model and download it onto the phone. Running it is the next step.
class LocalAiScreen(private val activity: Activity) : Screen {

    override val title = "LilyAi"

    private val handler = Handler(Looper.getMainLooper())
    private val freeSpace = activity.label("")
    private val cards = LocalModels.all.map { ModelCard(it) }

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.heading("Your own AI"))
        addView(
            activity.label(
                "Pick a model to download onto this phone. Models are large, so downloads use Wi-Fi only " +
                    "and carry on if you leave the app."
            )
        )
        addView(activity.label("Chatting with a downloaded model is the next step. For now this page downloads and manages the files."))
        addView(freeSpace)
        cards.forEach { addView(it.view) }
        addView(
            activity.label(
                "The files are kept in LilyAi's own storage on the phone. Uninstalling LilyAi deletes them."
            )
        )
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    // Refreshes the progress every second while this page is on screen.
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    override fun view(): View = scroll

    override fun onShow() {
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    override fun onHide() {
        handler.removeCallbacks(ticker)
    }

    private fun refresh() {
        freeSpace.text = "Free space on this phone: " + ModelDownloads.formatSize(ModelDownloads.freeBytes(activity))
        cards.forEach { it.update() }
    }

    // One model: its name and size, what is happening with it, and a button for the next action.
    private inner class ModelCard(private val model: LocalModel) {

        private var state: State = State.NotDownloaded
        private val status = activity.label("")
        private val button = activity.pinkButton("Download") { onButton() }

        val view = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(8))
            background = GradientDrawable().apply {
                setColor(Palette.white)
                cornerRadius = activity.dp(16).toFloat()
                setStroke(activity.dp(1), Palette.pink)
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, activity.dp(8), 0, activity.dp(8)) }
            addView(activity.heading("${model.name}  -  ${ModelDownloads.formatSize(model.approxBytes)}"))
            addView(activity.label(model.note))
            addView(status)
            addView(button)
        }

        fun update() {
            state = ModelDownloads.state(activity, model)
            when (val current = state) {
                is State.NotDownloaded -> {
                    status.text = "Not downloaded"
                    button.text = "Download"
                }
                is State.Downloading -> {
                    val total = if (current.totalBytes > 0) current.totalBytes else model.approxBytes
                    val percent = (current.doneBytes * 100 / total).toInt().coerceIn(0, 100)
                    val note = current.note ?: "Downloading"
                    val sizes = ModelDownloads.formatSize(current.doneBytes) + " of " + ModelDownloads.formatSize(total)
                    status.text = "$note: $percent% ($sizes)"
                    button.text = "Cancel"
                }
                is State.Ready -> {
                    status.text = "Ready on this phone (" + ModelDownloads.formatSize(current.sizeBytes) + ")"
                    button.text = "Delete from this phone"
                }
                is State.Failed -> {
                    status.text = current.message
                    button.text = "Try again"
                }
            }
        }

        private fun onButton() {
            when (state) {
                is State.Downloading -> {
                    ModelDownloads.remove(activity, model)
                    update()
                }
                is State.Ready -> confirmDelete()
                else -> {
                    ModelDownloads.start(activity, model)?.let { activity.toast(it) }
                    update()
                }
            }
        }

        private fun confirmDelete() {
            AlertDialog.Builder(activity)
                .setTitle("Delete ${model.name}?")
                .setMessage("The file will be removed from this phone. You can download it again later.")
                .setPositiveButton("Delete") { _, _ ->
                    ModelDownloads.remove(activity, model)
                    update()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
}
