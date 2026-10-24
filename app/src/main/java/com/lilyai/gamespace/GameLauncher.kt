package com.lilyai.gamespace

import android.content.Context

object GameLauncher {

    /** Returns false if the app has no launch intent (for example, it was uninstalled). */
    fun launch(context: Context, packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        context.startActivity(intent)
        return true
    }
}
