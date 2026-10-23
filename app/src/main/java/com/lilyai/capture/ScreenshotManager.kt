package com.lilyai.capture

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.view.Display
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ScreenshotManager {

    /** Takes a screenshot through the accessibility service and saves it to Pictures/LilyAi. */
    fun capture(service: AccessibilityService, onResult: (Result<Uri>) -> Unit) {
        service.takeScreenshot(
            Display.DEFAULT_DISPLAY,
            service.mainExecutor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                    onResult(runCatching { save(service, screenshot) })
                }

                override fun onFailure(errorCode: Int) {
                    onResult(Result.failure(IllegalStateException("takeScreenshot error $errorCode")))
                }
            }
        )
    }

    private fun save(service: AccessibilityService, screenshot: AccessibilityService.ScreenshotResult): Uri {
        val buffer = screenshot.hardwareBuffer
        try {
            val hardware = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                ?: error("Could not read the screenshot buffer")
            val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                ?: error("Could not copy the screenshot")
            hardware.recycle()

            // MediaStore, because Android 11 enforces scoped storage.
            val name = "LilyAi_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LilyAi")
            }
            val resolver = service.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create the media entry")

            val written = resolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            } ?: false
            bitmap.recycle()
            if (!written) {
                resolver.delete(uri, null, null)
                error("Could not write the screenshot")
            }
            return uri
        } finally {
            buffer.close()
        }
    }
}
