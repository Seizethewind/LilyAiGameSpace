package com.lilyai.ai

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

// Keys and model names for every provider. The keys of a provider are stored as one
// encrypted list (see KeyVault). Each call uses the next key in the list, then wraps around.
object ProviderSettings {

    private const val PREFS = "providers"

    // Before several keys were supported, Groq had one key and one model in the "ai" file.
    private const val OLD_PREFS = "ai"
    private const val OLD_KEY_ENCRYPTED = "groq_key_enc"
    private const val OLD_KEY_PLAIN = "groq_key"
    private const val OLD_MODEL = "groq_model"

    /** The saved keys in the order they were added. Empty if none (or they can no longer be decrypted). */
    fun keys(context: Context, provider: Provider): List<String> {
        prefs(context).getString(keysKey(provider), null)?.let { return decode(it) }
        return migrateOldKey(context, provider)
    }

    /** Adds a key at the end. Returns false if it is empty or already saved. */
    fun addKey(context: Context, provider: Provider, key: String): Boolean {
        val clean = key.trim()
        val keys = keys(context, provider)
        if (clean.isEmpty() || clean in keys) return false
        saveKeys(context, provider, keys + clean)
        return true
    }

    fun removeKeyAt(context: Context, provider: Provider, index: Int) {
        val keys = keys(context, provider)
        if (index !in keys.indices) return
        saveKeys(context, provider, keys.filterIndexed { position, _ -> position != index })
    }

    fun removeAllKeys(context: Context, provider: Provider) = saveKeys(context, provider, emptyList())

    /** The position (starting at 0) of the key the next call will use. */
    fun nextIndex(context: Context, provider: Provider): Int {
        val count = keys(context, provider).size
        return if (count == 0) 0 else prefs(context).getInt(nextKey(provider), 0) % count
    }

    /**
     * The keys for one call, starting with the one whose turn it is, then the rest in order.
     * The first one is the key to use. The others are only used if it is rejected.
     * The turn then moves on, so the next call starts with the next key (1, 2 ... last, then 1 again).
     */
    fun takeTurn(context: Context, provider: Provider): List<String> {
        val keys = keys(context, provider)
        if (keys.isEmpty()) return emptyList()
        val start = prefs(context).getInt(nextKey(provider), 0) % keys.size
        prefs(context).edit().putInt(nextKey(provider), (start + 1) % keys.size).apply()
        return keys.indices.map { keys[(start + it) % keys.size] }
    }

    /** The model name typed for this provider, or the provider's default. */
    fun model(context: Context, provider: Provider): String {
        val saved = prefs(context).getString(modelKey(provider), null)
            ?: if (provider == Provider.GROQ) {
                context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE).getString(OLD_MODEL, null)
            } else {
                null
            }
        return saved.orEmpty().ifBlank { provider.defaultModel }
    }

    fun setModel(context: Context, provider: Provider, model: String) {
        prefs(context).edit()
            .putString(modelKey(provider), model.trim().ifEmpty { provider.defaultModel })
            .apply()
    }

    private fun saveKeys(context: Context, provider: Provider, keys: List<String>) {
        val editor = prefs(context).edit()
        if (keys.isEmpty()) {
            editor.remove(keysKey(provider)).remove(nextKey(provider))
        } else {
            editor.putString(keysKey(provider), KeyVault.encrypt(JSONArray(keys).toString()))
        }
        editor.apply()
    }

    private fun decode(stored: String): List<String> {
        val json = KeyVault.decrypt(stored) ?: return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return List(array.length()) { array.getString(it) }
    }

    // Moves the single Groq key from the older version into the list, then deletes the old copy.
    private fun migrateOldKey(context: Context, provider: Provider): List<String> {
        if (provider != Provider.GROQ) return emptyList()
        val old = context.getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE)
        val key = old.getString(OLD_KEY_ENCRYPTED, null)?.let { KeyVault.decrypt(it) }
            ?: old.getString(OLD_KEY_PLAIN, "").orEmpty()
        if (key.isEmpty()) return emptyList()
        runCatching {
            saveKeys(context, provider, listOf(key))
            old.edit().remove(OLD_KEY_ENCRYPTED).remove(OLD_KEY_PLAIN).apply()
        }
        return listOf(key)
    }

    private fun keysKey(provider: Provider) = "keys:" + provider.id

    private fun nextKey(provider: Provider) = "next:" + provider.id

    private fun modelKey(provider: Provider) = "model:" + provider.id

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
