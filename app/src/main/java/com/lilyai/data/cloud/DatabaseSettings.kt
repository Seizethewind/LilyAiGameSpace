package com.lilyai.data.cloud

import android.content.Context
import android.content.SharedPreferences
import com.lilyai.ai.KeyVault

// The address and token of each Turso database. The address is plain text. The token is a
// secret, so it is stored encrypted (see KeyVault) and is never shown again once saved.
object DatabaseSettings {

    private const val PREFS = "databases"

    fun address(context: Context, database: TursoDatabase): String =
        prefs(context).getString(addressKey(database), "").orEmpty()

    fun saveAddress(context: Context, database: TursoDatabase, address: String) {
        prefs(context).edit().putString(addressKey(database), address.trim()).apply()
    }

    /** The saved token, or an empty text if there is none (or it can no longer be decrypted). */
    fun token(context: Context, database: TursoDatabase): String =
        prefs(context).getString(tokenKey(database), null)?.let { KeyVault.decrypt(it) }.orEmpty()

    /** Saves the token encrypted. An empty text removes it. */
    fun saveToken(context: Context, database: TursoDatabase, token: String) {
        val editor = prefs(context).edit()
        if (token.isBlank()) {
            editor.remove(tokenKey(database))
        } else {
            editor.putString(tokenKey(database), KeyVault.encrypt(token.trim()))
        }
        editor.apply()
    }

    /** True when both the address and the token are saved. */
    fun isReady(context: Context, database: TursoDatabase): Boolean =
        address(context, database).isNotBlank() && token(context, database).isNotEmpty()

    private fun addressKey(database: TursoDatabase) = "address:" + database.id

    private fun tokenKey(database: TursoDatabase) = "token:" + database.id

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
