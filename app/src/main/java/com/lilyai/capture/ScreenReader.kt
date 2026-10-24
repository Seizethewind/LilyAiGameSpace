package com.lilyai.capture

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** One line of text found on screen, with its position in screenshot pixels. */
data class ScreenText(val text: String, val bounds: Rect)

object ScreenReader {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Reads the text in a bitmap. Do not recycle the bitmap until onResult is called. */
    fun read(bitmap: Bitmap, onResult: (Result<List<ScreenText>>) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { result ->
                val lines = result.textBlocks
                    .flatMap { it.lines }
                    .mapNotNull { line ->
                        line.boundingBox?.let { ScreenText(line.text, it) }
                    }
                onResult(Result.success(lines))
            }
            .addOnFailureListener { onResult(Result.failure(it)) }
    }
}
