package com.lilyai.gamespace

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.ScreenshotManager

// Game library (tap to launch, long-press to remove) plus the Phase 1 screenshot proof.
class GameSpaceActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var list: ListView
    private lateinit var repository: GameRepository
    private lateinit var detector: GameDetector
    private var games: List<AppEntry> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = GameRepository(this)
        detector = GameDetector(packageManager)

        status = TextView(this).apply { textSize = 16f }
        val openSettings = Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }
        val screenshot = Button(this).apply {
            text = "Screenshot in 5 seconds"
            setOnClickListener { screenshotAfterDelay() }
        }
        val addGame = Button(this).apply {
            text = "Add game"
            setOnClickListener { showAddDialog() }
        }
        list = ListView(this).apply {
            setOnItemClickListener { _, _, position, _ ->
                val game = games[position]
                if (!GameLauncher.launch(this@GameSpaceActivity, game.packageName)) {
                    toast("Cannot launch ${game.name}")
                }
            }
            setOnItemLongClickListener { _, _, position, _ ->
                repository.remove(games[position].packageName)
                refreshList()
                true
            }
        }

        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(status)
            addView(openSettings)
            addView(screenshot)
            addView(addGame)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = if (LilyAccessibilityService.instance != null) {
            "Accessibility service: ON"
        } else {
            "Accessibility service: OFF"
        }
        refreshList()
    }

    private fun refreshList() {
        val saved = repository.packages()
        games = detector.installedApps().filter { it.packageName in saved }
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, games.map { it.name })
    }

    private fun showAddDialog() {
        val saved = repository.packages()
        // Apps that declare the game category come first.
        val candidates = detector.installedApps()
            .filter { it.packageName !in saved }
            .sortedByDescending { it.likelyGame }
        AlertDialog.Builder(this)
            .setTitle("Add game")
            .setItems(candidates.map { it.name }.toTypedArray()) { _, index ->
                repository.add(candidates[index].packageName)
                refreshList()
            }
            .show()
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
