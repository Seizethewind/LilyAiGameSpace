package com.lilyai.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.lilyai.gamespace.AppEntry
import com.lilyai.gamespace.GameDetector
import com.lilyai.gamespace.GameLauncher
import com.lilyai.gamespace.GameRepository
import com.lilyai.gamespace.PlaytimeTracker
import com.lilyai.overlay.OverlayControl

// The games you added. Tap one to launch it, hold to remove it from LilyAi.
class GamesScreen(private val activity: Activity) : Screen {

    override val title = "Games"

    private val repository = GameRepository(activity)
    private val detector = GameDetector(activity.packageManager)
    private val playtime = PlaytimeTracker(activity)
    private var games: List<AppEntry> = emptyList()

    private val hint: TextView = activity.label("")

    private val list = ListView(activity).apply {
        setOnItemClickListener { _, _, position, _ ->
            val game = games[position]
            // The overlay starts with the game when its permission is already granted.
            OverlayControl.start(activity, askPermission = false)
            if (!GameLauncher.launch(activity, game.packageName)) {
                activity.toast("Cannot launch ${game.name}")
            }
        }
        setOnItemLongClickListener { _, _, position, _ ->
            repository.remove(games[position].packageName)
            refresh()
            true
        }
    }

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.pinkButton("Add game") { showAddDialog() })
        addView(hint)
        addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    override fun view(): View = root

    override fun onShow() = refresh()

    // Each game shows today's minutes once usage access is allowed (see Setup).
    private fun refresh() {
        val saved = repository.packages()
        games = detector.installedApps().filter { it.packageName in saved }
        val minutes = if (playtime.hasAccess()) playtime.minutesToday() else null
        val labels = games.map { game ->
            if (minutes == null) game.name else "${game.name} - ${minutes[game.packageName] ?: 0} min today"
        }
        list.adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, labels)
        hint.text = if (games.isEmpty()) {
            "No games yet. Tap Add game."
        } else {
            "Tap a game to launch it. Hold to remove it from LilyAi."
        }
    }

    private fun showAddDialog() {
        val saved = repository.packages()
        // Apps that declare the game category come first.
        val candidates = detector.installedApps()
            .filter { it.packageName !in saved }
            .sortedByDescending { it.likelyGame }
        AlertDialog.Builder(activity)
            .setTitle("Add game")
            .setItems(candidates.map { it.name }.toTypedArray()) { _, index ->
                repository.add(candidates[index].packageName)
                refresh()
            }
            .show()
    }
}
