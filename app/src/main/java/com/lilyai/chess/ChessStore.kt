package com.lilyai.chess

import android.content.Context

// What LilyAi remembers about chess for each app: which side is at the bottom of the board, and
// how the pieces look (the calibration). Both are kept per game, like the focus box.
object ChessStore {

    private const val PREFS = "chess"

    fun whiteAtBottom(context: Context, game: String): Boolean =
        prefs(context).getBoolean("bottom:$game", true)

    fun setWhiteAtBottom(context: Context, game: String, whiteAtBottom: Boolean) {
        prefs(context).edit().putBoolean("bottom:$game", whiteAtBottom).apply()
    }

    /** Each piece is one line: its letter, the brightness of its fill, and its shape as 0s and 1s. */
    fun saveCalibration(context: Context, game: String, calibration: Calibration) {
        val text = calibration.templates.joinToString("\n") { template ->
            val bits = template.feature.mask.joinToString("") { if (it) "1" else "0" }
            "${template.piece.letter}|${template.feature.fillLum}|$bits"
        }
        prefs(context).edit().putString("calibration:$game", text).apply()
    }

    fun loadCalibration(context: Context, game: String): Calibration? {
        val text = prefs(context).getString("calibration:$game", null) ?: return null
        val templates = text.lines().mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size != 3) return@mapNotNull null
            val piece = parts[0].singleOrNull()?.let { Piece.fromLetter(it) } ?: return@mapNotNull null
            val lum = parts[1].toIntOrNull() ?: return@mapNotNull null
            val bits = parts[2]
            if (bits.length != BoardReader.GRID * BoardReader.GRID) return@mapNotNull null
            Template(piece, CellFeature(BooleanArray(bits.length) { bits[it] == '1' }, lum, 0f))
        }
        return if (templates.isEmpty()) null else Calibration(templates)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
