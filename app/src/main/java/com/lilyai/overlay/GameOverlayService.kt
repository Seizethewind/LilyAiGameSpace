package com.lilyai.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.autoplay.AutoPlayer
import com.lilyai.capture.ScreenReader
import com.lilyai.capture.ScreenText
import com.lilyai.capture.ScreenshotManager
import kotlin.math.abs

// A small draggable button over other apps. Tap it to show Screenshot, Read text, Teach, Auto and Stop.
// Runs as a foreground service so Android keeps it alive while a game is open.
class GameOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: LinearLayout? = null
    private var teachView: LinearLayout? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (root == null) showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AutoPlayer.stop()
        root?.let { windowManager.removeView(it) }
        root = null
        teachView = null
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "LilyAi overlay", NotificationManager.IMPORTANCE_LOW)
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("LilyAi overlay is on")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun showOverlay() {
        windowManager = getSystemService(WindowManager::class.java)

        val handle = Button(this).apply { text = "L" }
        // Shows the text found on screen. Tap it to hide it.
        val output = TextView(this).apply {
            visibility = View.GONE
            textSize = 12f
            maxLines = 12
            maxWidth = 700
            setTextColor(Color.WHITE)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(16, 16, 16, 16)
            setOnClickListener { visibility = View.GONE }
        }
        // The Teach list: tick the words Auto should tap for the current game.
        val teach = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setBackgroundColor(0xEE333333.toInt())
            setPadding(16, 16, 16, 16)
        }
        teachView = teach
        val screenshot = Button(this).apply {
            text = "Screenshot"
            visibility = View.GONE
            setOnClickListener { takeScreenshot() }
        }
        val readText = Button(this).apply {
            text = "Read text"
            visibility = View.GONE
            setOnClickListener { readScreen(output) }
        }
        val teachButton = Button(this).apply {
            text = "Teach"
            visibility = View.GONE
            setOnClickListener { teachScreen() }
        }
        val auto = Button(this).apply {
            text = AUTO_OFF
            visibility = View.GONE
        }
        val stop = Button(this).apply {
            text = "Stop"
            visibility = View.GONE
            setOnClickListener { stopSelf() }
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(handle)
            addView(screenshot)
            addView(readText)
            addView(teachButton)
            addView(auto)
            addView(stop)
            addView(output)
            addView(teach)
        }

        fun setMenuVisible(visible: Boolean) {
            val state = if (visible) View.VISIBLE else View.GONE
            screenshot.visibility = state
            readText.visibility = state
            teachButton.visibility = state
            auto.visibility = state
            stop.visibility = state
            if (visible) {
                // Auto can also be started or stopped by the assistant, so show the real state.
                auto.text = if (AutoPlayer.running) AUTO_ON else AUTO_OFF
            } else {
                output.visibility = View.GONE
                teach.visibility = View.GONE
            }
        }

        // Starting Auto folds the menu away so only the small handle stays on screen.
        auto.setOnClickListener {
            toggleAuto(auto)
            setMenuVisible(false)
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }

        handle.setOnTouchListener(dragAndTap(panel) {
            setMenuVisible(screenshot.visibility != View.VISIBLE)
        })

        windowManager.addView(panel, params)
        root = panel
    }

    // Drag moves the overlay. A touch that barely moves counts as a tap.
    private fun dragAndTap(panel: View, onTap: () -> Unit) = object : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (abs(dx) > TAP_SLOP || abs(dy) > TAP_SLOP) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        windowManager.updateViewLayout(panel, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!moved) onTap()
            }
            return true
        }
    }

    private fun takeScreenshot() {
        val service = LilyAccessibilityService.instance
        val panel = root
        if (service == null || panel == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        // Hide the overlay first so it does not appear in the screenshot.
        panel.visibility = View.INVISIBLE
        panel.postDelayed({
            ScreenshotManager.capture(service) { result ->
                panel.visibility = View.VISIBLE
                toast(result.fold({ "Screenshot saved" }, { "Failed: ${it.message}" }))
            }
        }, 400)
    }

    // Takes a screenshot, reads the text on it and shows the lines in the overlay.
    private fun readScreen(output: TextView) {
        val service = LilyAccessibilityService.instance
        val panel = root
        if (service == null || panel == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        output.visibility = View.GONE
        panel.visibility = View.INVISIBLE
        panel.postDelayed({
            ScreenshotManager.captureBitmap(service) { captured ->
                panel.visibility = View.VISIBLE
                captured.onSuccess { bitmap ->
                    ScreenReader.read(bitmap) { outcome ->
                        bitmap.recycle()
                        outcome.onSuccess { lines ->
                            output.text = if (lines.isEmpty()) {
                                "No text found"
                            } else {
                                lines.joinToString("\n") { it.text }
                            }
                            output.visibility = View.VISIBLE
                        }.onFailure { toast("Failed: ${it.message}") }
                    }
                }.onFailure { toast("Failed: ${it.message}") }
            }
        }, 400)
    }

    // Teach: read the current screen and let the user tick the words Auto should tap.
    private fun teachScreen() {
        val service = LilyAccessibilityService.instance
        val panel = root
        val teach = teachView
        if (service == null || panel == null || teach == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        teach.visibility = View.GONE
        panel.visibility = View.INVISIBLE
        panel.postDelayed({
            ScreenshotManager.captureBitmap(service) { captured ->
                panel.visibility = View.VISIBLE
                captured.onSuccess { bitmap ->
                    ScreenReader.read(bitmap) { outcome ->
                        bitmap.recycle()
                        outcome.onSuccess { lines -> showTeachList(teach, candidateWords(lines)) }
                            .onFailure { toast("Failed: ${it.message}") }
                    }
                }.onFailure { toast("Failed: ${it.message}") }
            }
        }, 400)
    }

    // Short lines are the likely buttons. Commas are removed because words are saved comma-separated.
    private fun candidateWords(lines: List<ScreenText>): List<String> =
        lines.sortedBy { it.bounds.top }
            .map { it.text.replace(",", " ").trim() }
            .filter { it.length in 2..30 }
            .distinctBy { it.lowercase() }
            .take(MAX_CHOICES)

    private fun showTeachList(teach: LinearLayout, words: List<String>) {
        val game = LilyAccessibilityService.foregroundGame
        val label = game?.let { appLabel(it) } ?: "default words"
        val saved = AutoPlayer.wordsFor(this, game).ifBlank { "none" }
        // The words are saved in the order they are ticked, which is their priority.
        val picked = mutableListOf<String>()

        fun line(content: String, size: Float) = TextView(this).apply {
            text = content
            textSize = size
            setTextColor(Color.WHITE)
        }

        val choices = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (words.isEmpty()) choices.addView(line("No text found on this screen", 14f))
        words.forEach { word ->
            choices.addView(
                CheckBox(this).apply {
                    text = word
                    setTextColor(Color.WHITE)
                    setOnCheckedChangeListener { _, checked ->
                        if (checked) picked.add(word) else picked.remove(word)
                    }
                }
            )
        }

        val save = Button(this).apply {
            text = "Save"
            setOnClickListener {
                if (picked.isEmpty()) {
                    toastShort("Tick the words to tap first")
                } else {
                    AutoPlayer.addWords(this@GameOverlayService, game, picked)
                    toastShort("Saved for $label: " + picked.joinToString())
                    teach.visibility = View.GONE
                }
            }
        }
        val clear = Button(this).apply {
            text = "Clear saved"
            setOnClickListener {
                AutoPlayer.saveWordsFor(this@GameOverlayService, game, "")
                toastShort("Cleared the words for $label")
                teach.visibility = View.GONE
            }
        }
        val close = Button(this).apply {
            text = "Close"
            setOnClickListener { teach.visibility = View.GONE }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(save)
            addView(clear)
            addView(close)
        }

        teach.removeAllViews()
        teach.addView(line("Teach: $label", 14f))
        teach.addView(line("Saved: $saved", 12f))
        teach.addView(
            ScrollView(this).apply { addView(choices) },
            LinearLayout.LayoutParams(TEACH_WIDTH, TEACH_HEIGHT)
        )
        teach.addView(buttons)
        teach.visibility = View.VISIBLE
    }

    private fun appLabel(packageName: String): String =
        runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        }.getOrDefault(packageName)

    // Starts or stops the autoplay loop with the words saved for the current game.
    // Drag the handle away from anything the loop needs to tap: taps go to whatever is on top.
    private fun toggleAuto(button: Button) {
        if (AutoPlayer.running) {
            AutoPlayer.stop()
            button.text = AUTO_OFF
            toastShort("Auto stopped")
            return
        }
        val targets = AutoPlayer.targets(this)
        if (targets.isEmpty()) {
            toast("No words yet. Use Teach on a game screen, or set default words in the LilyAi app")
            return
        }
        if (LilyAccessibilityService.instance == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        AutoPlayer.start(targets) { event ->
            toastShort(event)
            if (!AutoPlayer.running) button.text = AUTO_OFF
        }
        button.text = AUTO_ON
        toastShort("Auto started: " + targets.joinToString())
    }

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    private fun toastShort(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()

    private companion object {
        const val CHANNEL_ID = "overlay"
        const val NOTIFICATION_ID = 1
        const val TAP_SLOP = 10
        const val AUTO_ON = "Auto: ON"
        const val AUTO_OFF = "Auto: OFF"
        const val TEACH_WIDTH = 600
        const val TEACH_HEIGHT = 500
        const val MAX_CHOICES = 40
    }
}
