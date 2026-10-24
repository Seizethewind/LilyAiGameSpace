package com.lilyai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.lilyai.gamespace.GameRepository

class LilyAccessibilityService : AccessibilityService() {

    private val repository by lazy { GameRepository(this) }

    override fun onServiceConnected() {
        instance = this
    }

    // Remember which library game was last on screen. Teach and Auto use that game's words.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val windowEvent = event ?: return
        if (windowEvent.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = windowEvent.packageName?.toString() ?: return
        if (packageName in repository.packages()) foregroundGame = packageName
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        foregroundGame = null
        return super.onUnbind(intent)
    }

    /** Taps the screen at (x, y) in screen pixels. onDone(true) means the tap was performed. */
    fun tap(x: Float, y: Float, onDone: (Boolean) -> Unit = {}) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val dispatched = dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) = onDone(true)
                override fun onCancelled(gestureDescription: GestureDescription?) = onDone(false)
            },
            null
        )
        if (!dispatched) onDone(false)
    }

    companion object {
        private const val TAP_DURATION_MS = 50L

        // Set while the user has the service turned on in Android settings.
        @Volatile
        var instance: LilyAccessibilityService? = null
            private set

        // The library game that was last on screen, or null if none has been seen yet.
        @Volatile
        var foregroundGame: String? = null
            private set
    }
}
