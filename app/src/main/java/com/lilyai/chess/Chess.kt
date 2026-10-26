package com.lilyai.chess

enum class Side { WHITE, BLACK }

enum class PieceType(val letter: Char) {
    PAWN('p'),
    KNIGHT('n'),
    BISHOP('b'),
    ROOK('r'),
    QUEEN('q'),
    KING('k')
}

data class Piece(val type: PieceType, val side: Side) {

    /** The letter used in chess notation (FEN): a capital for white, a small letter for black. */
    val letter: Char
        get() = if (side == Side.WHITE) type.letter.uppercaseChar() else type.letter

    companion object {
        fun fromLetter(letter: Char): Piece? {
            val type = PieceType.values().firstOrNull { it.letter == letter.lowercaseChar() } ?: return null
            return Piece(type, if (letter.isUpperCase()) Side.WHITE else Side.BLACK)
        }
    }
}

/** Where every piece stands at the start of a game. Files are 0 to 7 for a to h, ranks 0 to 7 for 1 to 8. */
object StartPosition {

    private val backRank = listOf(
        PieceType.ROOK, PieceType.KNIGHT, PieceType.BISHOP, PieceType.QUEEN,
        PieceType.KING, PieceType.BISHOP, PieceType.KNIGHT, PieceType.ROOK
    )

    fun pieceAt(file: Int, rank: Int): Piece? = when (rank) {
        0 -> Piece(backRank[file], Side.WHITE)
        1 -> Piece(PieceType.PAWN, Side.WHITE)
        6 -> Piece(PieceType.PAWN, Side.BLACK)
        7 -> Piece(backRank[file], Side.BLACK)
        else -> null
    }
}

/**
 * The file and rank (a1 is 0 and 0) of the square drawn at this row and column of the picture of
 * the board, counting from the top left. It depends on which side is at the bottom.
 */
fun squareAt(row: Int, col: Int, whiteAtBottom: Boolean): Pair<Int, Int> =
    if (whiteAtBottom) col to (7 - row) else (7 - col) to row

/** What was seen on the board. Each square holds a piece letter, a dot for empty, or ? for not recognized. */
class BoardPosition(private val squares: CharArray) {

    val unknownCount: Int get() = squares.count { it == '?' }

    val pieceCount: Int get() = squares.count { it != '.' && it != '?' }

    /** The piece placement part of a FEN, from rank 8 down to rank 1. */
    fun fen(): String = (7 downTo 0).joinToString("/") { rank ->
        val row = StringBuilder()
        var empty = 0
        for (file in 0..7) {
            val square = squares[rank * 8 + file]
            if (square == '.') {
                empty++
            } else {
                if (empty > 0) {
                    row.append(empty)
                    empty = 0
                }
                row.append(square)
            }
        }
        if (empty > 0) row.append(empty)
        row.toString()
    }

    /** A picture of the board in text, with a summary and the FEN. Use a font with equal-width letters. */
    fun describe(): String {
        val rows = (7 downTo 0).map { rank ->
            (rank + 1).toString() + "  " + (0..7).joinToString(" ") { file -> squares[rank * 8 + file].toString() }
        }
        val files = "   a b c d e f g h"
        val summary = if (unknownCount == 0) {
            "$pieceCount pieces"
        } else {
            "$pieceCount pieces, $unknownCount not recognized"
        }
        val fenLine = if (unknownCount == 0) "FEN: " + fen() else "Some squares were not recognized"
        return (rows + files + summary + fenLine).joinToString("\n")
    }
}
