package com.lilyai.autoplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lilyai.accessibility.LilyAccessibilityService

// Autoplay loop: every couple of seconds, look for any of the chosen words on screen and
// tap the first one found. Runs until stop() is called or something keeps failing.
object AutoPlayer {

    private const val INTERVAL_MS = 2000L
    private const val MAX_ERRORS = 3
    private const val PREFS = "autoplay"
    private const val KEY_WORDS = "words"

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var errors = 0

    var running = false
        private set

    /** The words as the user typed them, for example "Next, OK, Skip". */
    fun rawTargets(context: Context): String =
        prefs(context).getString(KEY_WORDS, "").orEmpty()

    fun saveTargets(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_WORDS, raw).apply()
    }

    /** The words to look for, in priority order. */
    fun targets(context: Context): List<String> =
        rawTargets(context).split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** Starts the loop. onEvent reports each tap and why the loop stopped by itself. */
    fun start(targets: List<String>, onEvent: (String) -> Unit) {
        if (running) return
        running = true
        errors = 0
        generation++
        step(generation, targets, onEvent)
    }

    fun stop() {
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
    }

    private fun step(run: Int, targets: List<String>, onEvent: (String) -> Unit) {
        if (!running || run != generation) return
        val service = LilyAccessibilityService.instance
        if (service == null) {
            stop()
            onEvent("Auto stopped: the accessibility service is off")
            return
        }
        ScreenActions.tapAny(service, targets) { result ->
            if (!running || run != generation) return@tapAny
            val tapped = result.getOrNull()
            val failure = result.exceptionOrNull()
            when {
                tapped != null -> {
                    errors = 0
                    onEvent("Tapped ${tapped.text}")
                }
                // Nothing to tap on this screen is normal, so keep looping quietly.
                failure is NoSuchElementException -> errors = 0
                else -> {
                    errors++
                    if (errors >= MAX_ERRORS) {
                        stop()
                        onEvent("Auto stopped: ${failure?.message}")
                        return@tapAny
                    }
                }
            }
            handler.postDelayed({ step(run, targets, onEvent) }, INTERVAL_MS)
        }
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
