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
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.ScreenReader
import com.lilyai.capture.ScreenshotManager
import kotlin.math.abs

// A small draggable button over other apps. Tap it to show Screenshot, Read text and Stop.
// Runs as a foreground service so Android keeps it alive while a game is open.
class GameOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var root: LinearLayout? = null

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
        root?.let { windowManager.removeView(it) }
        root = null
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
            addView(stop)
            addView(output)
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
            val show = if (screenshot.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            screenshot.visibility = show
            readText.visibility = show
            stop.visibility = show
            if (show == View.GONE) output.visibility = View.GONE
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

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

    private companion object {
        const val CHANNEL_ID = "overlay"
        const val NOTIFICATION_ID = 1
        const val TAP_SLOP = 10
    }
}
