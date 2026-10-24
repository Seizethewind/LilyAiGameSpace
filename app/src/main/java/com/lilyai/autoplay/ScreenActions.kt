package com.lilyai.autoplay

import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.ScreenReader
import com.lilyai.capture.ScreenText
import com.lilyai.capture.ScreenshotManager

// Building blocks for autoplay: look at the screen, find something, act on it.
object ScreenActions {

    /** Taps the first line of text containing [query] (ignoring case). */
    fun tapText(
        service: LilyAccessibilityService,
        query: String,
        onResult: (Result<ScreenText>) -> Unit
    ) = tapAny(service, listOf(query), onResult)

    /**
     * Takes a screenshot, finds the first word from [queries] that appears on screen and taps
     * the center of its line. List order is priority. Fails with NoSuchElementException when
     * none of the words is on screen.
     */
    fun tapAny(
        service: LilyAccessibilityService,
        queries: List<String>,
        onResult: (Result<ScreenText>) -> Unit
    ) {
        ScreenshotManager.captureBitmap(service) { captured ->
            captured.onSuccess { bitmap ->
                ScreenReader.read(bitmap) { outcome ->
                    bitmap.recycle()
                    outcome.onSuccess { lines ->
                        val match = queries.firstNotNullOfOrNull { query ->
                            lines.firstOrNull { it.text.contains(query, ignoreCase = true) }
                        }
                        if (match == null) {
                            val wanted = queries.joinToString(" / ")
                            onResult(Result.failure(NoSuchElementException("Not found on screen: $wanted")))
                        } else {
                            // Screenshot pixels are screen pixels, so the bounds can be tapped directly.
                            service.tap(match.bounds.exactCenterX(), match.bounds.exactCenterY()) { tapped ->
                                onResult(
                                    if (tapped) Result.success(match)
                                    else Result.failure(IllegalStateException("Tap failed or was cancelled"))
                                )
                            }
                        }
                    }.onFailure { onResult(Result.failure(it)) }
                }
            }.onFailure { onResult(Result.failure(it)) }
        }
    }
}
