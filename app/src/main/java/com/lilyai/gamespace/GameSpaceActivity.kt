package com.lilyai.gamespace

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.drawerlayout.widget.DrawerLayout
import com.lilyai.ui.ChatScreen
import com.lilyai.ui.GamesScreen
import com.lilyai.ui.HomeScreen
import com.lilyai.ui.Palette
import com.lilyai.ui.RecordsScreen
import com.lilyai.ui.Screen
import com.lilyai.ui.SecretsScreen
import com.lilyai.ui.SetupScreen
import com.lilyai.ui.dp
import com.lilyai.ui.heading
import com.lilyai.ui.softGradient

// The app shell: a front page with just the name, and a drawer on the left (swipe in from the
// left edge, or tap the menu icon) that opens Games, Records, Secrets, Chat and Setup.
// A page can open another page on top of it (Secrets opens a provider). Back goes down one.
class GameSpaceActivity : Activity() {

    private lateinit var drawer: DrawerLayout
    private lateinit var frame: FrameLayout
    private lateinit var screenTitle: TextView
    private lateinit var home: Screen
    private var current: Screen? = null
    private val backStack = ArrayDeque<Screen>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        home = HomeScreen(this)
        // Emoji as icons for now. They need no image files.
        val entries = listOf(
            "\uD83C\uDFAE" to GamesScreen(this),
            "\uD83D\uDCD2" to RecordsScreen(this),
            "\uD83D\uDD11" to SecretsScreen(this, this::open),
            "\uD83D\uDCAC" to ChatScreen(this),
            "\u2699\uFE0F" to SetupScreen(this)
        )

        val menuButton = TextView(this).apply {
            text = "\u2630"
            textSize = 28f
            setTextColor(Palette.plum)
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { drawer.openDrawer(Gravity.START) }
        }
        screenTitle = TextView(this).apply {
            textSize = 20f
            setTextColor(Palette.plum)
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(menuButton)
            addView(screenTitle)
        }
        frame = FrameLayout(this)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = softGradient()
            addView(topBar)
            addView(frame, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = softGradient()
            setPadding(dp(16), dp(48), dp(16), dp(16))
            addView(heading("LilyAi"))
            entries.forEach { (icon, screen) ->
                addView(TextView(this@GameSpaceActivity).apply {
                    text = "$icon   ${screen.title}"
                    textSize = 18f
                    setTextColor(Palette.plum)
                    setPadding(dp(8), dp(14), dp(8), dp(14))
                    setOnClickListener {
                        // Picking from the drawer starts fresh, with nothing to go back to.
                        backStack.clear()
                        show(screen)
                    }
                })
            }
        }

        drawer = DrawerLayout(this).apply {
            addView(content, DrawerLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(panel, DrawerLayout.LayoutParams(dp(280), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START))
        }
        setContentView(drawer)

        window.statusBarColor = Palette.pinkSoft
        window.navigationBarColor = Palette.white
        window.insetsController?.setSystemBarsAppearance(
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
        )

        show(home)
    }

    override fun onResume() {
        super.onResume()
        current?.onShow()
    }

    override fun onPause() {
        super.onPause()
        current?.onHide()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        when {
            drawer.isDrawerOpen(Gravity.START) -> drawer.closeDrawers()
            backStack.isNotEmpty() -> show(backStack.removeLast())
            current !== home -> show(home)
            else -> super.onBackPressed()
        }
    }

    // Opens a page on top of the current one, so Back returns to it.
    private fun open(screen: Screen) {
        current?.let { backStack.addLast(it) }
        show(screen)
    }

    private fun show(screen: Screen) {
        current?.onHide()
        current = screen
        frame.removeAllViews()
        frame.addView(screen.view())
        // The front page shows only the name, so its title stays empty.
        screenTitle.text = if (screen === home) "" else screen.title
        screen.onShow()
        drawer.closeDrawers()
    }
}
