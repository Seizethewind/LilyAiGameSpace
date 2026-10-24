package com.lilyai.overlay

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

object OverlayControl {

    /**
     * Starts the overlay. Returns false when the "display over other apps" permission is missing.
     * With [askPermission], it also opens the Android screen where the permission is granted.
     */
    fun start(activity: Activity, askPermission: Boolean): Boolean {
        if (!Settings.canDrawOverlays(activity)) {
            if (askPermission) {
                Toast.makeText(
                    activity.applicationContext,
                    "Allow LilyAi to display over other apps, then tap Show overlay again",
                    Toast.LENGTH_LONG
                ).show()
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))
                )
            }
            return false
        }
        activity.startForegroundService(Intent(activity, GameOverlayService::class.java))
        return true
    }
}
