package com.lilyai.chess

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What one square looks like. The mask marks the small blocks that differ from the square's own
 * background, which is the shape of the piece. The fill is how bright the middle of the piece is.
 */
class CellFeature(val mask: BooleanArray, val fillLum: Int, val coverage: Float)

class Template(val piece: Piece, val feature: CellFeature)

/** How each piece looks in one chess app, learned from the starting position. */
class Calibration(val templates: List<Template>)

// Reads a chess board from a screenshot without knowing the app's piece style beforehand.
// 1. Calibrate: with the game at the starting position, every piece is where it is known to be,
//    so the picture of each piece is saved under its name.
// 2. Read: each square is compared with the saved pictures and given the name of the closest one.
// Each square is compared with its own background colour, so light squares, dark squares and a
// highlighted last move all work the same way.
object BoardReader {

    const val GRID = 16
    private const val MARGIN = 0.12f
    private const val FG_DISTANCE = 60
    private const val OCCUPIED_COVERAGE = 0.10f
    private const val MIN_SIMILARITY = 0.30f
    private const val LUM_WEIGHT = 0.35f
    private const val MAX_WRONG_START_SQUARES = 2

    /** Learns the pieces. [board] is the board's box in screen pixels, the same as the screenshot. */
    fun calibrate(bitmap: Bitmap, board: Rect, whiteAtBottom: Boolean): Result<Calibration> = runCatching {
        require(abs(board.width() - board.height()) <= max(board.width(), board.height()) * 0.12f) {
            "The focus box is not square. Draw it tightly around the 8 by 8 squares."
        }
        val templates = mutableListOf<Template>()
        var wrong = 0
        for (row in 0..7) for (col in 0..7) {
            val (file, rank) = squareAt(row, col, whiteAtBottom)
            val expected = StartPosition.pieceAt(file, rank)
            val feature = feature(bitmap, cellRect(board, row, col, bitmap))
            val occupied = feature.coverage >= OCCUPIED_COVERAGE
            if (expected == null) {
                if (occupied) wrong++
            } else if (occupied) {
                templates.add(Template(expected, feature))
            } else {
                wrong++
            }
        }
        check(wrong <= MAX_WRONG_START_SQUARES) {
            "$wrong squares do not look like the starting position. Check that the game is at the " +
                "starting position, that White or Black at the bottom is set correctly, and that the " +
                "box covers exactly the 8 by 8 squares."
        }
        Calibration(templates)
    }

    /** Reads the position on the board. */
    fun read(bitmap: Bitmap, board: Rect, whiteAtBottom: Boolean, calibration: Calibration): BoardPosition {
        val squares = CharArray(64) { '.' }
        for (row in 0..7) for (col in 0..7) {
            val (file, rank) = squareAt(row, col, whiteAtBottom)
            val feature = feature(bitmap, cellRect(board, row, col, bitmap))
            squares[rank * 8 + file] = if (feature.coverage < OCCUPIED_COVERAGE) {
                '.'
            } else {
                classify(feature, calibration)?.letter ?: '?'
            }
        }
        return BoardPosition(squares)
    }

    // The saved picture that is closest in shape, with a nudge towards the one with a similar fill
    // brightness, which tells white pieces from black ones.
    private fun classify(feature: CellFeature, calibration: Calibration): Piece? {
        var best: Template? = null
        var bestScore = -1f
        var bestSimilarity = 0f
        for (template in calibration.templates) {
            val similarity = similarity(feature.mask, template.feature.mask)
            val score = similarity - LUM_WEIGHT * abs(feature.fillLum - template.feature.fillLum) / 255f
            if (score > bestScore) {
                best = template
                bestScore = score
                bestSimilarity = similarity
            }
        }
        return if (best != null && bestSimilarity >= MIN_SIMILARITY) best.piece else null
    }

    // How much of the two shapes overlaps: 1 is identical, 0 is nothing in common.
    private fun similarity(a: BooleanArray, b: BooleanArray): Float {
        var both = 0
        var either = 0
        for (i in a.indices) {
            if (a[i] && b[i]) both++
            if (a[i] || b[i]) either++
        }
        return if (either == 0) 0f else both.toFloat() / either
    }

    private fun cellRect(board: Rect, row: Int, col: Int, bitmap: Bitmap): Rect {
        val cell = Rect(
            board.left + (board.width() * col / 8f).roundToInt(),
            board.top + (board.height() * row / 8f).roundToInt(),
            board.left + (board.width() * (col + 1) / 8f).roundToInt(),
            board.top + (board.height() * (row + 1) / 8f).roundToInt()
        )
        cell.intersect(0, 0, bitmap.width, bitmap.height)
        check(cell.width() >= 8 && cell.height() >= 8) { "The focus box is too small or off the screen." }
        return cell
    }

    private fun feature(bitmap: Bitmap, cell: Rect): CellFeature {
        val w = cell.width()
        val h = cell.height()
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, cell.left, cell.top, w, h)
        val background = dominantColor(pixels)

        // Only the middle of the square is used, which leaves out the coordinate letters in the corners.
        val padX = (w * MARGIN).toInt()
        val padY = (h * MARGIN).toInt()
        val regionW = w - 2 * padX
        val regionH = h - 2 * padY

        val mask = BooleanArray(GRID * GRID)
        var allLum = 0L
        var allCount = 0
        var centerLum = 0L
        var centerCount = 0
        for (gy in 0 until GRID) {
            val y0 = padY + gy * regionH / GRID
            val y1 = min(h, max(y0 + 1, padY + (gy + 1) * regionH / GRID))
            for (gx in 0 until GRID) {
                val x0 = padX + gx * regionW / GRID
                val x1 = min(w, max(x0 + 1, padX + (gx + 1) * regionW / GRID))
                var r = 0L
                var g = 0L
                var b = 0L
                var n = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val p = pixels[y * w + x]
                        r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF
                        b += p and 0xFF
                        n++
                    }
                }
                if (n == 0) continue
                val ar = (r / n).toInt()
                val ag = (g / n).toInt()
                val ab = (b / n).toInt()
                val distance = abs(ar - background[0]) + abs(ag - background[1]) + abs(ab - background[2])
                if (distance > FG_DISTANCE) {
                    mask[gy * GRID + gx] = true
                    val lum = (299 * ar + 587 * ag + 114 * ab) / 1000
                    allLum += lum
                    allCount++
                    if (gx in 4..11 && gy in 4..11) {
                        centerLum += lum
                        centerCount++
                    }
                }
            }
        }
        val fill = when {
            centerCount > 0 -> centerLum / centerCount
            allCount > 0 -> allLum / allCount
            else -> 0L
        }
        return CellFeature(mask, fill.toInt(), allCount / (GRID * GRID).toFloat())
    }

    // The most common colour in the square. Most of a square is its own background, even with a piece on it.
    private fun dominantColor(pixels: IntArray): IntArray {
        val counts = IntArray(4096)
        for (p in pixels) counts[colorKey(p)]++
        var bestKey = 0
        for (k in counts.indices) if (counts[k] > counts[bestKey]) bestKey = k
        var r = 0L
        var g = 0L
        var b = 0L
        var n = 0
        for (p in pixels) {
            if (colorKey(p) == bestKey) {
                r += (p shr 16) and 0xFF
                g += (p shr 8) and 0xFF
                b += p and 0xFF
                n++
            }
        }
        return intArrayOf((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    // The top 4 bits of each colour channel, as one number from 0 to 4095.
    private fun colorKey(p: Int): Int =
        (((p shr 20) and 0xF) shl 8) or (((p shr 12) and 0xF) shl 4) or ((p shr 4) and 0xF)
}
