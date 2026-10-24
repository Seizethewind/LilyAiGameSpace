package com.lilyai.ui

import android.app.Activity
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.autoplay.AutoPlayer
import com.lilyai.autoplay.ScreenActions
import com.lilyai.capture.ScreenshotManager
import com.lilyai.gamespace.PlaytimeTracker
import com.lilyai.overlay.OverlayControl

// Permissions, default Auto words, and the developer test tools.
class SetupScreen(private val activity: Activity) : Screen {

    override val title = "Setup"

    private val accessibilityStatus = activity.label("")
    private val overlayStatus = activity.label("")
    private val usageStatus = activity.label("")
    private val autoWords = activity.input("Default Auto words, for example Next, OK, Skip")
    private val tapQuery = activity.input("Text to tap, for example Next")
    private var taps = 0

    // The target counts real touches. Auto-tap taps it through the accessibility service,
    // so the count going up proves gestures work.
    private val tapTarget = activity.pinkButton("Tap target: 0") {}.apply {
        setOnClickListener {
            taps++
            text = "Tap target: $taps"
        }
    }

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))

        addView(activity.heading("Permissions"))
        addView(accessibilityStatus)
        addView(activity.pinkButton("Open accessibility settings") {
            activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        addView(overlayStatus)
        addView(activity.pinkButton("Show overlay") {
            OverlayControl.start(activity, askPermission = true)
        })
        addView(usageStatus)
        addView(activity.pinkButton("Allow usage access") {
            activity.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        })

        addView(activity.heading("Auto"))
        addView(autoWords)
        addView(activity.label("Default words are used when a game has none. Use Teach on the overlay to save words for one game."))

        addView(activity.heading("Developer tools"))
        addView(activity.pinkButton("Screenshot in 5 seconds") { screenshotAfterDelay() })
        addView(tapTarget)
        addView(activity.pinkButton("Auto-tap the target 5 times") { autoTap(tapTarget, 5) })
        addView(tapQuery)
        addView(activity.pinkButton("Tap this text in 5 seconds") {
            tapTextAfterDelay(tapQuery.text.toString().trim())
        })
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    override fun onShow() {
        accessibilityStatus.text = "Accessibility service: " +
            if (LilyAccessibilityService.instance != null) "ON" else "OFF"
        overlayStatus.text = "Display over other apps: " +
            if (Settings.canDrawOverlays(activity)) "allowed" else "not allowed"
        usageStatus.text = "Usage access (for playtime): " +
            if (PlaytimeTracker(activity).hasAccess()) "allowed" else "not allowed"
        autoWords.setText(AutoPlayer.rawTargets(activity))
    }

    override fun onHide() {
        AutoPlayer.saveTargets(activity, autoWords.text.toString())
    }

    // Taps the middle of the target button through the accessibility service, one tap at a time.
    private fun autoTap(target: View, remaining: Int) {
        if (remaining == 0) return
        val service = LilyAccessibilityService.instance
        if (service == null) {
            activity.toast("Turn on the LilyAi accessibility service first")
            return
        }
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val x = location[0] + target.width / 2f
        val y = location[1] + target.height / 2f
        service.tap(x, y) { performed ->
            if (performed) {
                target.postDelayed({ autoTap(target, remaining - 1) }, 500)
            } else {
                activity.toast("Tap failed or was cancelled")
            }
        }
    }

    // The delay gives you time to switch to a game before LilyAi looks at the screen.
    private fun tapTextAfterDelay(query: String) {
        if (query.isEmpty()) {
            activity.toast("Type the text to tap first")
            return
        }
        if (LilyAccessibilityService.instance == null) {
            activity.toast("Turn on the LilyAi accessibility service first")
            return
        }
        activity.toast("Switch to your game. Looking for \"$query\" in 5 seconds")
        Handler(Looper.getMainLooper()).postDelayed({
            val service = LilyAccessibilityService.instance
            if (service == null) {
                activity.toast("Accessibility service was turned off")
            } else {
                ScreenActions.tapText(service, query) { result ->
                    activity.toast(result.fold({ "Tapped \"${it.text}\"" }, { "Failed: ${it.message}" }))
                }
            }
        }, 5000)
    }

    // The delay gives you time to switch to a game before the capture.
    private fun screenshotAfterDelay() {
        if (LilyAccessibilityService.instance == null) {
            activity.toast("Turn on the LilyAi accessibility service first")
            return
        }
        activity.toast("Switch to your game. Capturing in 5 seconds")
        Handler(Looper.getMainLooper()).postDelayed({
            val service = LilyAccessibilityService.instance
            if (service == null) {
                activity.toast("Accessibility service was turned off")
            } else {
                ScreenshotManager.capture(service) { result ->
                    activity.toast(result.fold({ "Saved: $it" }, { "Failed: ${it.message}" }))
                }
            }
        }, 5000)
    }
}
