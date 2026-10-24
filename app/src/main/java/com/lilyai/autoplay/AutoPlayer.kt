package com.lilyai.autoplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lilyai.accessibility.LilyAccessibilityService

// Autoplay loop: every couple of seconds, look for any of the chosen words on screen and
// tap the first one found. Runs until stop() is called or something keeps failing.
// Each game can have its own words. The default words are used when a game has none.
object AutoPlayer {

    private const val INTERVAL_MS = 2000L
    private const val MAX_ERRORS = 3
    private const val PREFS = "autoplay"
    private const val KEY_WORDS = "words"
    private const val KEY_GAME_PREFIX = "words:"

    private val handler = Handler(Looper.getMainLooper())
    private var generation = 0
    private var errors = 0

    var running = false
        private set

    /** The default words as the user typed them, for example "Next, OK, Skip". */
    fun rawTargets(context: Context): String =
        prefs(context).getString(KEY_WORDS, "").orEmpty()

    fun saveTargets(context: Context, raw: String) {
        prefs(context).edit().putString(KEY_WORDS, raw).apply()
    }

    /** The words saved for one game (its package name), or the default words when [game] is null. */
    fun wordsFor(context: Context, game: String?): String =
        if (game == null) rawTargets(context) else prefs(context).getString(KEY_GAME_PREFIX + game, "").orEmpty()

    fun saveWordsFor(context: Context, game: String?, raw: String) {
        if (game == null) {
            saveTargets(context, raw)
        } else {
            prefs(context).edit().putString(KEY_GAME_PREFIX + game, raw).apply()
        }
    }

    /** Adds words after the saved ones, skipping any that are already there. */
    fun addWords(context: Context, game: String?, words: List<String>) {
        val existing = parseWords(wordsFor(context, game))
        val added = words.filter { word -> existing.none { it.equals(word, ignoreCase = true) } }
        saveWordsFor(context, game, (existing + added).joinToString(", "))
    }

    fun parseWords(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** The words to look for now, in priority order: the current game's words, else the defaults. */
    fun targets(context: Context): List<String> {
        val game = LilyAccessibilityService.foregroundGame
        return parseWords(wordsFor(context, game)).ifEmpty { parseWords(rawTargets(context)) }
    }

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
