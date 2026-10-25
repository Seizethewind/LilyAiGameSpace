package com.lilyai.ai

import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.StatFs
import java.io.File
import java.util.Locale

// Downloads model files with Android's own download service. It carries on in the background,
// resumes after a lost connection, and shows a notification. Files go into LilyAi's own storage
// (Android/data/com.lilyai/files/models), so no storage permission is needed.
object ModelDownloads {

    sealed class State {
        object NotDownloaded : State()
        data class Downloading(val doneBytes: Long, val totalBytes: Long, val note: String?) : State()
        data class Ready(val sizeBytes: Long) : State()
        data class Failed(val message: String) : State()
    }

    private class Progress(val status: Int, val done: Long, val total: Long, val reason: Int)

    private const val FOLDER = "models"
    private const val PREFS = "local_models"
    private const val NO_DOWNLOAD = -1L
    private const val SPACE_MARGIN_BYTES = 300_000_000L

    /** Where the model file is (or will be). Null if the phone's storage is not available. */
    fun file(context: Context, model: LocalModel): File? =
        context.getExternalFilesDir(FOLDER)?.let { File(it, model.fileName) }

    fun freeBytes(context: Context): Long {
        val dir = context.getExternalFilesDir(FOLDER) ?: return 0L
        return StatFs(dir.path).availableBytes
    }

    fun state(context: Context, model: LocalModel): State {
        val file = file(context, model)
        val savedId = prefs(context).getLong(model.id, NO_DOWNLOAD)
        if (savedId != NO_DOWNLOAD) {
            val progress = query(context, savedId)
            if (progress != null) {
                return when (progress.status) {
                    DownloadManager.STATUS_SUCCESSFUL ->
                        if (file != null && file.exists()) State.Ready(file.length()) else State.NotDownloaded
                    DownloadManager.STATUS_FAILED -> State.Failed(failureText(progress.reason))
                    else -> State.Downloading(progress.done, progress.total, waitingNote(progress))
                }
            }
            // The system no longer knows this download.
            prefs(context).edit().remove(model.id).apply()
        }
        // A complete-looking file placed in the folder by hand also counts.
        if (file != null && file.exists() && file.length() >= model.approxBytes / 10 * 9) {
            return State.Ready(file.length())
        }
        return State.NotDownloaded
    }

    /** Starts the download. Returns a message if it cannot start, or null once it has started. */
    fun start(context: Context, model: LocalModel): String? {
        val file = file(context, model) ?: return "Storage is not available"
        if (freeBytes(context) < model.approxBytes + SPACE_MARGIN_BYTES) {
            return "Not enough free space. This model needs about ${formatSize(model.approxBytes)}."
        }
        // The download service refuses to write over an existing file.
        file.delete()
        val request = DownloadManager.Request(Uri.parse(model.url))
            .setTitle("LilyAi: ${model.name}")
            .setDescription("Downloading the model")
            .setDestinationInExternalFilesDir(context, FOLDER, model.fileName)
            .setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val id = context.getSystemService(DownloadManager::class.java).enqueue(request)
        prefs(context).edit().putLong(model.id, id).apply()
        return null
    }

    /** Cancels a running download, or deletes a finished one. */
    fun remove(context: Context, model: LocalModel) {
        val savedId = prefs(context).getLong(model.id, NO_DOWNLOAD)
        if (savedId != NO_DOWNLOAD) context.getSystemService(DownloadManager::class.java).remove(savedId)
        file(context, model)?.delete()
        prefs(context).edit().remove(model.id).apply()
    }

    fun formatSize(bytes: Long): String =
        if (bytes >= 1_000_000_000L) {
            String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        } else {
            "${bytes / 1_000_000} MB"
        }

    private fun query(context: Context, id: Long): Progress? {
        val manager = context.getSystemService(DownloadManager::class.java)
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return Progress(
                status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                done = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            )
        }
    }

    private fun waitingNote(progress: Progress): String? = when (progress.status) {
        DownloadManager.STATUS_PENDING -> "Starting"
        DownloadManager.STATUS_PAUSED -> when (progress.reason) {
            DownloadManager.PAUSED_QUEUED_FOR_WIFI,
            DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "Waiting for Wi-Fi"
            DownloadManager.PAUSED_WAITING_TO_RETRY -> "Waiting to retry"
            else -> "Paused"
        }
        else -> null
    }

    private fun failureText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough space on the phone"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage is not available"
        in 400..599 -> "The server refused the download (code $reason)"
        else -> "The download failed (code $reason)"
    }

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
