package com.lilyai.capture

import android.content.Context
import android.graphics.Rect

// The box the user drew on screen for the AI to focus on, saved per game. Positions are in screen
// pixels, the same as screenshots, so a box can be applied to text found in a screenshot directly.
object FocusArea {

    private const val PREFS = "focus"

    /**
     * The saved box for this game (or the default box when [game] is null), or null if there is
     * none. A box saved with a different screen size, for example before rotating, is ignored.
     */
    fun get(context: Context, game: String?, screenWidth: Int, screenHeight: Int): Rect? {
        val parts = prefs(context).getString(key(game), null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: return null
        if (parts.size != 6 || parts[4] != screenWidth || parts[5] != screenHeight) return null
        return Rect(parts[0], parts[1], parts[2], parts[3])
    }

    fun save(context: Context, game: String?, area: Rect, screenWidth: Int, screenHeight: Int) {
        val text = listOf(area.left, area.top, area.right, area.bottom, screenWidth, screenHeight)
            .joinToString(",")
        prefs(context).edit().putString(key(game), text).apply()
    }

    fun clear(context: Context, game: String?) {
        prefs(context).edit().remove(key(game)).apply()
    }

    /** The lines whose center is inside the box. With no box, every line is kept. */
    fun filter(lines: List<ScreenText>, area: Rect?): List<ScreenText> =
        if (area == null) lines else lines.filter { area.contains(it.bounds.centerX(), it.bounds.centerY()) }

    private fun key(game: String?) = "focus:" + (game ?: "default")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
