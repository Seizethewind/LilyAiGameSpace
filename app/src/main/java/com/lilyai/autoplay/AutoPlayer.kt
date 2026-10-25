package com.lilyai.autoplay

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.FocusArea
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Autoplay loop: every couple of seconds, look at the screen. If a stop word is there, stop.
// Otherwise tap the first of the chosen words that is there, looking only inside the focus area
// when one is set. It can be paused and resumed. Each game can have its own words. The default
// words are used when a game has none.
object AutoPlayer {

    private const val DEFAULT_INTERVAL_MS = 2000L
    private val INTERVALS_MS = listOf(1000L, 2000L, 3000L, 5000L)
    private const val MAX_ERRORS = 3
    private const val MAX_EVENTS = 50
    private const val PREFS = "autoplay"
    private const val KEY_WORDS = "words"
    private const val KEY_GAME_PREFIX = "words:"
    private const val KEY_STOP = "stop"
    private const val KEY_STOP_GAME_PREFIX = "stop:"
    private const val KEY_INTERVAL = "interval_ms"

    private val handler = Handler(Looper.getMainLooper())
    private val clock = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val events = ArrayDeque<String>()
    private var appContext: Context? = null
    private var generation = 0
    private var errors = 0
    private var scans = 0
    private var taps = 0
    private var currentInterval = DEFAULT_INTERVAL_MS

    var running = false
        private set

    /** True while Auto is on but waiting. It keeps its place and does nothing until resumed. */
    var paused = false
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
        saveWordsFor(context, game, merge(parseWords(wordsFor(context, game)), words))
    }

    /** Stop words: if one is on screen, Auto stops instead of tapping. */
    fun stopWordsFor(context: Context, game: String?): String =
        prefs(context).getString(stopKey(game), "").orEmpty()

    fun saveStopWordsFor(context: Context, game: String?, raw: String) {
        prefs(context).edit().putString(stopKey(game), raw).apply()
    }

    fun addStopWords(context: Context, game: String?, words: List<String>) {
        saveStopWordsFor(context, game, merge(parseWords(stopWordsFor(context, game)), words))
    }

    fun parseWords(raw: String): List<String> =
        raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    /** The words to look for now, in priority order: the current game's words, else the defaults. */
    fun targets(context: Context): List<String> {
        val game = LilyAccessibilityService.foregroundGame
        return parseWords(wordsFor(context, game)).ifEmpty { parseWords(rawTargets(context)) }
    }

    /** The stop words for the current game, else the default stop words. */
    fun stopWords(context: Context): List<String> {
        val game = LilyAccessibilityService.foregroundGame
        return parseWords(stopWordsFor(context, game)).ifEmpty { parseWords(stopWordsFor(context, null)) }
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
    fun summary(): String {
        val state = if (!running) "OFF" else if (paused) "PAUSED" else "ON"
        return "Auto $state: $scans scans, $taps taps"
    }

    /** The latest events, newest first. */
    fun recentEvents(): List<String> = events.toList().asReversed()

    /** Starts the loop. onEvent reports each tap and why the loop stopped by itself. */
    fun start(context: Context, targets: List<String>, onEvent: (String) -> Unit) {
        if (running) return
        appContext = context.applicationContext
        running = true
        paused = false
        errors = 0
        scans = 0
        taps = 0
        currentInterval = prefs(context).getLong(KEY_INTERVAL, DEFAULT_INTERVAL_MS)
        generation++
        val stops = stopWords(context)
        val stopNote = if (stops.isEmpty()) "" else " (stops on: " + stops.joinToString(", ") + ")"
        record("Started: " + targets.joinToString(", ") + stopNote)
        step(generation, targets, stops, onEvent)
    }

    fun pause() {
        if (!running || paused) return
        paused = true
        record("Paused")
    }

    fun resume() {
        if (!running || !paused) return
        paused = false
        record("Resumed")
    }

    fun stop(reason: String = "Stopped") {
        if (running) record(reason)
        running = false
        paused = false
        generation++
        handler.removeCallbacksAndMessages(null)
    }

    private fun halt(reason: String, onEvent: (String) -> Unit) {
        stop(reason)
        onEvent(reason)
    }

    private fun step(run: Int, targets: List<String>, stops: List<String>, onEvent: (String) -> Unit) {
        if (!running || run != generation) return
        val service = LilyAccessibilityService.instance
        if (service == null) {
            halt("Auto stopped: the accessibility service is off", onEvent)
            return
        }
        // Paused: the loop stays alive but does nothing until it is resumed.
        if (paused) {
            scheduleNext(run, targets, stops, onEvent)
            return
        }
        ScreenActions.readScreen(service) { result ->
            if (!running || run != generation) return@readScreen
            scans++
            val look = result.getOrNull()
            if (look == null) {
                errors++
                val message = result.exceptionOrNull()?.message
                record("Error: $message")
                if (errors >= MAX_ERRORS) {
                    halt("Auto stopped: $message", onEvent)
                } else {
                    scheduleNext(run, targets, stops, onEvent)
                }
                return@readScreen
            }
            errors = 0

            // Paused while this look was being taken: do nothing with it.
            if (paused) {
                scheduleNext(run, targets, stops, onEvent)
                return@readScreen
            }

            // A stop word anywhere on the screen wins, so Auto stops even for a popup outside the focus area.
            val stopHit = ScreenActions.findFirst(look.lines, stops)
            if (stopHit != null) {
                halt("Auto stopped: saw ${stopHit.text}", onEvent)
                return@readScreen
            }

            // Words to tap are only looked for inside the focus area, if one is set.
            val game = LilyAccessibilityService.foregroundGame
            val focus = appContext?.let { FocusArea.get(it, game, look.width, look.height) }
            val match = ScreenActions.findFirst(FocusArea.filter(look.lines, focus), targets)

            // Nothing to tap on this screen is normal, so keep looping quietly.
            if (match == null) {
                scheduleNext(run, targets, stops, onEvent)
                return@readScreen
            }
            ScreenActions.tapLine(service, match) { tapped ->
                if (!running || run != generation) return@tapLine
                if (tapped) {
                    errors = 0
                    taps++
                    record("Tapped ${match.text}")
                    onEvent("Tapped ${match.text}")
                } else {
                    errors++
                    record("Error: the tap failed")
                    if (errors >= MAX_ERRORS) {
                        halt("Auto stopped: taps keep failing", onEvent)
                        return@tapLine
                    }
                }
                scheduleNext(run, targets, stops, onEvent)
            }
        }
    }

    private fun scheduleNext(run: Int, targets: List<String>, stops: List<String>, onEvent: (String) -> Unit) {
        handler.postDelayed({ step(run, targets, stops, onEvent) }, currentInterval)
    }

    // Existing words keep their place. New ones are added after them, skipping duplicates.
    private fun merge(existing: List<String>, words: List<String>): String {
        val added = words.filter { word -> existing.none { it.equals(word, ignoreCase = true) } }
        return (existing + added).joinToString(", ")
    }

    private fun stopKey(game: String?) = if (game == null) KEY_STOP else KEY_STOP_GAME_PREFIX + game

    private fun record(message: String) {
        events.addLast(clock.format(Date()) + " " + message)
        while (events.size > MAX_EVENTS) events.removeFirst()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
