package com.lilyai.gamespace

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.ScreenshotManager

// Phase 1 proof screen: turn on the accessibility service and take a screenshot.
class GameSpaceActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply { textSize = 16f }
        val openSettings = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val screenshot = Button(this).apply {
            text = "Screenshot in 5 seconds"
            setOnClickListener { screenshotAfterDelay() }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(status)
            addView(openSettings)
            addView(screenshot)
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (LilyAccessibilityService.instance != null) {
            "Accessibility service: ON"
        } else {
            "Accessibility service: OFF"
        }
    }

    // The delay gives you time to switch to a game before the capture.
    private fun screenshotAfterDelay() {
        if (LilyAccessibilityService.instance == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        toast("Switch to your game. Capturing in 5 seconds")
        Handler(Looper.getMainLooper()).postDelayed({
            val service = LilyAccessibilityService.instance
            if (service == null) {
                toast("Accessibility service was turned off")
            } else {
                ScreenshotManager.capture(service) { result ->
                    toast(result.fold({ "Saved: $it" }, { "Failed: ${it.message}" }))
                }
            }
        }, 5000)
    }

    private fun toast(message: String) =
        Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
}
