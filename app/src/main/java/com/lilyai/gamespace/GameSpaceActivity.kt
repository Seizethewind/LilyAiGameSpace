package com.lilyai.gamespace

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.ai.GroqClient
import com.lilyai.ai.ToolRegistry
import com.lilyai.autoplay.AutoPlayer
import com.lilyai.autoplay.ScreenActions
import com.lilyai.capture.ScreenshotManager
import com.lilyai.overlay.GameOverlayService

// Game library (tap to launch, long-press to remove) plus the overlay, Auto words, Ask LilyAi and test controls.
class GameSpaceActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var autoWords: EditText
    private lateinit var apiKey: EditText
    private lateinit var list: ListView
    private lateinit var repository: GameRepository
    private lateinit var detector: GameDetector
    private var games: List<AppEntry> = emptyList()
    private val tools by lazy { ToolRegistry(applicationContext) }

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
        val overlay = Button(this).apply {
            text = "Show overlay"
            setOnClickListener { showOverlay(askPermission = true) }
        }
        // Words for the overlay's Auto mode, in priority order. Saved when you leave this screen.
        autoWords = EditText(this).apply {
            hint = "Auto mode words, for example Next, OK, Skip"
            setSingleLine()
            setText(AutoPlayer.rawTargets(this@GameSpaceActivity))
        }
        // Ask LilyAi: a question goes to Groq with your key. Saved when you leave this screen.
        apiKey = EditText(this).apply {
            hint = "Groq API key"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine()
            setText(GroqClient.savedKey(this@GameSpaceActivity))
        }
        val question = EditText(this).apply {
            hint = "Ask LilyAi, or tell it to launch a game or start Auto"
        }
        val answer = TextView(this).apply { setTextIsSelectable(true) }
        val ask = Button(this).apply {
            text = "Ask LilyAi"
            setOnClickListener { askLily(question.text.toString().trim(), answer) }
        }
        // Tap test: the target counts real touches. The second button taps it through
        // the accessibility service, so the count going up proves gestures work.
        var taps = 0
        val tapTarget = Button(this).apply {
            text = "Tap target: 0"
            setOnClickListener {
                taps++
                text = "Tap target: $taps"
            }
        }
        val autoTap = Button(this).apply {
            text = "Auto-tap the target 5 times"
            setOnClickListener { autoTap(tapTarget, 5) }
        }
        // Find-and-tap test: type a word, switch to a game, and LilyAi taps that word on screen.
        val tapQuery = EditText(this).apply {
            hint = "Text to tap, for example Next"
            setSingleLine()
        }
        val tapText = Button(this).apply {
            text = "Tap this text in 5 seconds"
            setOnClickListener { tapTextAfterDelay(tapQuery.text.toString().trim()) }
        }
        val addGame = Button(this).apply {
            text = "Add game"
            setOnClickListener { showAddDialog() }
        }
        list = ListView(this).apply {
            setOnItemClickListener { _, _, position, _ ->
                val game = games[position]
                // The overlay starts with the game when the permission is already granted.
                showOverlay(askPermission = false)
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

        // The controls scroll in the top half so the game list keeps the bottom half.
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(status)
            addView(openSettings)
            addView(screenshot)
            addView(overlay)
            addView(autoWords)
            addView(apiKey)
            addView(question)
            addView(ask)
            addView(answer)
            addView(tapTarget)
            addView(autoTap)
            addView(tapQuery)
            addView(tapText)
            addView(addGame)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            addView(
                ScrollView(this@GameSpaceActivity).apply { addView(controls) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            )
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

    override fun onPause() {
        super.onPause()
        AutoPlayer.saveTargets(this, autoWords.text.toString())
        GroqClient.saveKey(this, apiKey.text.toString().trim())
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

    private fun showOverlay(askPermission: Boolean) {
        if (!Settings.canDrawOverlays(this)) {
            if (askPermission) {
                toast("Allow LilyAi to display over other apps, then tap Show overlay again")
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            return
        }
        startForegroundService(Intent(this, GameOverlayService::class.java))
    }

    private fun askLily(question: String, answer: TextView) {
        val key = apiKey.text.toString().trim()
        if (key.isEmpty()) {
            toast("Paste your Groq API key first")
            return
        }
        if (question.isEmpty()) {
            toast("Type a question first")
            return
        }
        // Save the words as typed so the assistant sees and changes the current ones.
        AutoPlayer.saveTargets(this, autoWords.text.toString())
        answer.text = "Thinking..."
        GroqClient.ask(key, question, tools) { result ->
            answer.text = result.fold({ it }, { "Failed: ${it.message}" })
            // The assistant may have changed the Auto words.
            autoWords.setText(AutoPlayer.rawTargets(this))
        }
    }

    // Taps the middle of the target button through the accessibility service, one tap at a time.
    private fun autoTap(target: View, remaining: Int) {
        if (remaining == 0) return
        val service = LilyAccessibilityService.instance
        if (service == null) {
            toast("Turn on the LilyAi accessibility service first")
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
                toast("Tap failed or was cancelled")
            }
        }
    }

    // The delay gives you time to switch to a game before LilyAi looks at the screen.
    private fun tapTextAfterDelay(query: String) {
        if (query.isEmpty()) {
            toast("Type the text to tap first")
            return
        }
        if (LilyAccessibilityService.instance == null) {
            toast("Turn on the LilyAi accessibility service first")
            return
        }
        toast("Switch to your game. Looking for \"$query\" in 5 seconds")
        Handler(Looper.getMainLooper()).postDelayed({
            val service = LilyAccessibilityService.instance
            if (service == null) {
                toast("Accessibility service was turned off")
            } else {
                ScreenActions.tapText(service, query) { result ->
                    toast(result.fold({ "Tapped \"${it.text}\"" }, { "Failed: ${it.message}" }))
                }
            }
        }, 5000)
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
