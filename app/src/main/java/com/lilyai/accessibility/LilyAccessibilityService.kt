package com.lilyai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class LilyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
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
    }
}
