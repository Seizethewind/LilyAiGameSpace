package com.lilyai.autoplay

import com.lilyai.accessibility.LilyAccessibilityService
import com.lilyai.capture.ScreenReader
import com.lilyai.capture.ScreenText
import com.lilyai.capture.ScreenshotManager

// Building blocks for autoplay: look at the screen, find something, act on it.
object ScreenActions {

    /**
     * Takes a screenshot, finds the first line of text containing [query] (ignoring case)
     * and taps its center. The result is the text that was tapped.
     */
    fun tapText(
        service: LilyAccessibilityService,
        query: String,
        onResult: (Result<ScreenText>) -> Unit
    ) {
        ScreenshotManager.captureBitmap(service) { captured ->
            captured.onSuccess { bitmap ->
                ScreenReader.read(bitmap) { outcome ->
                    bitmap.recycle()
                    outcome.onSuccess { lines ->
                        val match = lines.firstOrNull { it.text.contains(query, ignoreCase = true) }
                        if (match == null) {
                            onResult(Result.failure(NoSuchElementException("\"$query\" not found on screen")))
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
