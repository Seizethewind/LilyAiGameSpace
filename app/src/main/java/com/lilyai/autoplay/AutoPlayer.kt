package com.lilyai.autoplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lilyai.accessibility.LilyAccessibilityService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Autoplay loop: every couple of seconds, look for any of the chosen words on screen and
// tap the first one found. Runs until stop() is called or something keeps failing.
// Each game can have its own words. The default words are used when a game has none.
object AutoPlayer {

    private const val DEFAULT_INTERVAL_MS = 2000L
    private val INTERVALS_MS = listOf(1000L, 2000L, 3000L, 5000L)
    private const val MAX_ERRORS = 3
    private const val MAX_EVENTS = 50
    private const val PREFS = "autoplay"
    private const val KEY_WORDS = "words"
    private const val KEY_GAME_PREFIX = "words:"
    private const val KEY_INTERVAL = "interval_ms"

    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val events = ArrayDeque<String>()
    private var generation = 0
    private var errors = 0
    private var scans = 0
    private var taps = 0
    private var currentInterval = DEFAULT_INTERVAL_MS

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

    /** Seconds between looks at the screen. */
    fun intervalSeconds(context: Context): Long =
        prefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS) / 1000

    /** Switches to the next speed (1, 2, 3 or 5 seconds), keeps it, and returns it in milliseconds. */
    fun cycleInterval(context: Context): Long {
        val current = prefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS)
        val next = INTERVALS_MS[(INTERVALS_MS.indexOf(current) + 1) % INTERVALS_MS.size]
        prefs(context).edit().putLong(KEY_INTERVAL, next).apply()
        currentInterval = next
        return next
    }

    /** One line: whether Auto is on, and how many looks and taps since it started. */
    fun summary(): String = "Auto " + (if (running) "ON" else "OFF") + ": $scans scans, $taps taps"

    /** The latest events, newest first. */
    fun recentEvents(): List<String> = events.toList().asReversed()

    /** Starts the loop. onEvent reports each tap and why the loop stopped by itself. */
    fun start(context: Context, targets: List<String>, onEvent: (String) -> Unit) {
        if (running) return
        running = true
        errors = 0
        scans = 0
        taps = 0
        currentInterval = prefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS)
        generation++
        record("Started: " + targets.joinToString(", "))
        step(generation, targets, onEvent)
    }

    fun stop(reason: String = "Stopped") {
        if (running) record(reason)
        running = false
        generation++
        handler.removeCallbacksAndMessages(null)
    }

    private fun step(run: Int, targets: List<String>, onEvent: (String) -> Unit) {
        if (!running || run != generation) return
        val service = LilyAccessibilityService.instance
        if (service == null) {
            val reason = "Auto stopped: the accessibility service is off"
            stop(reason)
            onEvent(reason)
            return
        }
        ScreenActions.tapAny(service, targets) { result ->
            if (!running || run != generation) return@tapAny
            scans++
            val tapped = result.getOrNull()
            val failure = result.exceptionOrNull()
            when {
                tapped != null -> {
                    errors = 0
                    taps++
                    record("Tapped ${tapped.text}")
                    onEvent("Tapped ${tapped.text}")
                }
                // Nothing to tap on this screen is normal, so keep looping quietly.
                failure is NoSuchElementException -> errors = 0
                else -> {
                    errors++
                    record("Error: ${failure?.message}")
                    if (errors >= MAX_ERRORS) {
                        val reason = "Auto stopped: ${failure?.message}"
                        stop(reason)
                        onEvent(reason)
                        return@tapAny
                    }
                }
            }
            handler.postDelayed({ step(run, targets, onEvent) }, currentInterval)
        }
    }

    private fun record(message: String) {
        events.addLast(clock.format(Date()) + " " + message)
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
