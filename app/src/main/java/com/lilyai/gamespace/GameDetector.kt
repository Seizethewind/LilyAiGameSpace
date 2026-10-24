package com.lilyai.gamespace

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

data class AppEntry(val packageName: String, val name: String, val likelyGame: Boolean)

class GameDetector(private val packageManager: PackageManager) {

    /** Launchable apps on the phone, excluding LilyAi itself, sorted by name. */
    fun installedApps(): List<AppEntry> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != OWN_PACKAGE }
            .map {
                AppEntry(
                    packageName = it.packageName,
                    name = it.loadLabel(packageManager).toString(),
                    // Only apps that declare the game category are flagged. Android has no
                    // reliable "is a game" flag, so manual add is the real way to build the list.
                    likelyGame = it.category == ApplicationInfo.CATEGORY_GAME
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private companion object {
        const val OWN_PACKAGE = "com.lilyai"
    }
}
