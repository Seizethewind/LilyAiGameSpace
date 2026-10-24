package com.lilyai.gamespace

import android.content.Context

// Stores the package names of the games the user added.
// SharedPreferences is enough for a list of names. Room comes when profiles and
// sessions need real tables.
class GameRepository(context: Context) {

    private val prefs = context.getSharedPreferences("games", Context.MODE_PRIVATE)

    fun packages(): Set<String> = HashSet(prefs.getStringSet(KEY, emptySet()) ?: emptySet())

    fun add(packageName: String) {
        prefs.edit().putStringSet(KEY, packages() + packageName).apply()
    }

    fun remove(packageName: String) {
        prefs.edit().putStringSet(KEY, packages() - packageName).apply()
    }

    private companion object {
        const val KEY = "packages"
    }
}
