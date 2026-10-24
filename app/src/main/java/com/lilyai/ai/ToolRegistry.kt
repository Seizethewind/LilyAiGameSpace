package com.lilyai.ai

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.autoplay.AutoPlayer
import com.lilyai.gamespace.AppEntry
import com.lilyai.gamespace.GameDetector
import com.lilyai.gamespace.GameLauncher
import com.lilyai.gamespace.GameRepository
import com.lilyai.gamespace.PlaytimeTracker
import com.lilyai.overlay.GameOverlayService
import org.json.JSONArray
import org.json.JSONObject

// The only things the AI can do on the phone. It can call these tools and nothing else.
class ToolRegistry(private val context: Context) {

    private val repository = GameRepository(context)
    private val detector = GameDetector(context.packageManager)

    /** The tool list in the format the chat API expects. */
    fun definitions(): JSONArray = JSONArray()
        .put(tool("list_games", "List the games in the user's LilyAi game library."))
        .put(
            tool(
                "launch_game",
                "Launch a game from the library by name.",
                "name" to "The game name, or part of it"
            )
        )
        .put(
            tool(
                "get_playtime",
                "How many minutes each library game was played.",
                "period" to "today or week"
            )
        )
        .put(
            tool(
                "set_auto_words",
                "Set the words Auto mode looks for on screen and taps, in priority order. " +
                    "They are saved for the game that was last on screen, or as the default words if none.",
                "words" to "Comma-separated words, for example Next, OK, Skip"
            )
        )
        .put(tool("start_auto", "Start Auto mode: repeatedly tap the first saved word found on screen."))
        .put(tool("stop_auto", "Stop Auto mode."))

    /** Runs one tool on the main thread. The returned text is what the model reads back. */
    fun execute(name: String, args: JSONObject): String = when (name) {
        "list_games" -> listGames()
        "launch_game" -> launchGame(args.optString("name"))
        "get_playtime" -> playtime(args.optString("period"))
        "set_auto_words" -> setAutoWords(args.optString("words"))
        "start_auto" -> startAuto()
        "stop_auto" -> {
            AutoPlayer.stop()
            "Auto stopped"
        }
        else -> "Unknown tool: $name"
    }

    private fun libraryGames(): List<AppEntry> {
        val saved = repository.packages()
        return detector.installedApps().filter { it.packageName in saved }
    }

    private fun listGames(): String =
        libraryGames().joinToString(", ") { it.name }.ifEmpty { "The library is empty" }

    private fun launchGame(name: String): String {
        if (name.isBlank()) return "No game name given"
        val matches = libraryGames().filter { it.name.contains(name, ignoreCase = true) }
        return when {
            matches.isEmpty() -> "No game matching '$name' in the library"
            matches.size > 1 -> "More than one match: " + matches.joinToString(", ") { it.name }
            else -> {
                val game = matches[0]
                startOverlay()
                if (GameLauncher.launch(context, game.packageName)) {
                    "Launched ${game.name}"
                } else {
                    "Could not launch ${game.name}"
                }
            }
        }
    }

    private fun playtime(period: String): String {
        val tracker = PlaytimeTracker(context)
        if (!tracker.hasAccess()) {
            return "Usage access is not allowed. The user must allow it for LilyAi in Android settings first"
        }
        val games = libraryGames()
        if (games.isEmpty()) return "The library is empty"
        val week = period.contains("week", ignoreCase = true)
        val minutes = if (week) tracker.minutesLast7Days() else tracker.minutesToday()
        val label = if (week) "last 7 days" else "today"
        return "Playtime ($label): " + games.joinToString(", ") {
            "${it.name} ${minutes[it.packageName] ?: 0} min"
        }
    }

    private fun setAutoWords(words: String): String {
        val parsed = AutoPlayer.parseWords(words)
        if (parsed.isEmpty()) return "No words given"
        // Words go to the game that was last on screen, or to the default words.
        val game = LilyAccessibilityService.foregroundGame
        val gameName = libraryGames().firstOrNull { it.packageName == game }?.name
        AutoPlayer.saveWordsFor(context, if (gameName != null) game else null, words)
        val target = gameName ?: "all games"
        return "Auto words set for $target: " + parsed.joinToString(", ")
    }

    private fun startAuto(): String {
        val targets = AutoPlayer.targets(context)
        if (targets.isEmpty()) return "No Auto words are set"
        if (LilyAccessibilityService.instance == null) {
            return "The accessibility service is off. The user must turn it on first"
        }
        if (AutoPlayer.running) return "Auto is already running"
        // Auto needs the overlay on screen so the user always has a Stop control.
        if (!startOverlay()) return "Overlay permission is missing. The user must allow it first"
        AutoPlayer.start(targets) { event ->
            Toast.makeText(context, event, Toast.LENGTH_SHORT).show()
        }
        return "Auto started with: " + targets.joinToString(", ")
    }

    private fun startOverlay(): Boolean {
        if (!Settings.canDrawOverlays(context)) return false
        return runCatching {
            context.startForegroundService(Intent(context, GameOverlayService::class.java))
        }.isSuccess
    }

    private fun tool(name: String, description: String, vararg params: Pair<String, String>): JSONObject {
        val properties = JSONObject()
        params.forEach { (key, text) ->
            properties.put(key, JSONObject().put("type", "string").put("description", text))
        }
        val parameters = JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", JSONArray(params.map { it.first }))
        return JSONObject()
            .put("type", "function")
            .put(
                "function",
                JSONObject().put("name", name).put("description", description).put("parameters", parameters)
            )
    }
}
