package com.lilyai.autoplay

import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.ScreenReader
import com.lilyai.capture.ScreenText
import com.lilyai.capture.ScreenshotManager

/** The lines of text found on one screenshot, and the screenshot's size in pixels. */
class ScreenLook(val lines: List<ScreenText>, val width: Int, val height: Int)

// Building blocks for autoplay: look at the screen, find something, act on it.
object ScreenActions {

    /** Takes a screenshot and reads the lines of text on it. */
    fun readScreen(
        service: LilyAccessibilityService,
        onResult: (Result<ScreenLook>) -> Unit
    ) {
        ScreenshotManager.captureBitmap(service) { captured ->
            captured.onSuccess { bitmap ->
                val width = bitmap.width
                val height = bitmap.height
                ScreenReader.read(bitmap) { outcome ->
                    bitmap.recycle()
                    onResult(outcome.map { lines -> ScreenLook(lines, width, height) })
                }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }

    /**
     * The first line containing one of [queries] (ignoring case). List order is priority:
     * a word earlier in the list wins over a later one.
     */
    fun findFirst(lines: List<ScreenText>, queries: List<String>): ScreenText? =
        queries.firstNotNullOfOrNull { query ->
            lines.firstOrNull { it.text.contains(query, ignoreCase = true) }
        }

    /** Taps the center of a line. Screenshot pixels are screen pixels, so no conversion is needed. */
    fun tapLine(
        service: LilyAccessibilityService,
        line: ScreenText,
        onDone: (Boolean) -> Unit
    ) = service.tap(line.bounds.exactCenterX(), line.bounds.exactCenterY(), onDone)

    /** Taps the first line containing [query] (ignoring case). */
    fun tapText(
        service: LilyAccessibilityService,
        query: String,
        onResult: (Result<ScreenText>) -> Unit
    ) = tapAny(service, listOf(query), onResult)

    /**
     * Finds the first word from [queries] that appears on screen and taps its line.
     * Fails with NoSuchElementException when none of the words is on screen.
     */
    fun tapAny(
        service: LilyAccessibilityService,
        queries: List<String>,
        onResult: (Result<ScreenText>) -> Unit
    ) {
        readScreen(service) { result ->
            result.onSuccess { look ->
                val match = findFirst(look.lines, queries)
                if (match == null) {
                    val wanted = queries.joinToString(" / ")
                    onResult(Result.failure(NoSuchElementException("Not found on screen: $wanted")))
                } else {
                    tapLine(service, match) { tapped ->
                        onResult(
                            if (tapped) Result.success(match)
                            else Result.failure(IllegalStateException("Tap failed or was cancelled"))
                        )
                    }
                }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }
}
