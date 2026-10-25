package com.lilyai.ui

import android.app.Activity
import android.app.AlertDialog
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.lilyai.data.cloud.DatabaseSettings
import com.lilyai.data.cloud.TursoClient
import com.lilyai.data.cloud.TursoDatabase

// The page for one Turso database: its address, its token, and a button that tests the
// connection. The token is encrypted, hidden, and can only be reset, like the Groq keys.
class TursoScreen(
    private val activity: Activity,
    private val database: TursoDatabase
) : Screen {

    override val title = database.displayName

    private val addressInput = activity.input("libsql://name-org.turso.io")
    private val tokenArea = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val testResult = activity.label("")

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.heading("${database.icon}  ${database.displayName}"))
        addView(activity.label(database.purpose))
        addView(activity.heading("Database address"))
        addView(activity.label("The database's URL from Turso. It starts with libsql:// or https://."))
        addView(addressInput)
        addView(activity.pinkButton("Save address") {
            DatabaseSettings.saveAddress(activity, database, addressInput.text.toString())
            activity.toast("Address saved")
        })
        addView(activity.heading("Token"))
        addView(tokenArea)
        addView(activity.heading("Connection"))
        addView(activity.label("The test only asks the database for the number 1. It reads and changes nothing."))
        addView(activity.pinkButton("Test connection") { testConnection() })
        addView(testResult)
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    override fun onShow() {
        addressInput.setText(DatabaseSettings.address(activity, database))
        testResult.text = ""
        renderToken()
    }

    private fun renderToken() {
        tokenArea.removeAllViews()
        if (DatabaseSettings.token(activity, database).isNotEmpty()) {
            tokenArea.addView(
                activity.label(
                    "A token is saved and encrypted on this phone. It is hidden and cannot be edited. " +
                        "Reset it to enter a new one."
                )
            )
            tokenArea.addView(activity.pinkButton("Reset token") { confirmReset() })
        } else {
            val tokenInput = activity.input("Paste the database token", password = true)
            tokenArea.addView(tokenInput)
            tokenArea.addView(activity.pinkButton("Save token") { saveToken(tokenInput.text.toString().trim()) })
        }
    }

    private fun saveToken(token: String) {
        if (token.isEmpty()) {
            activity.toast("Paste the token first")
            return
        }
        runCatching { DatabaseSettings.saveToken(activity, database, token) }
            .onSuccess { renderToken() }
            .onFailure { activity.toast("Could not encrypt the token on this phone: ${it.message}") }
    }

    private fun confirmReset() {
        AlertDialog.Builder(activity)
            .setTitle("Reset the saved token?")
            .setMessage("The token will be removed from this phone. You will need to paste it again.")
            .setPositiveButton("Reset") { _, _ ->
                DatabaseSettings.saveToken(activity, database, "")
                renderToken()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun testConnection() {
        val address = addressInput.text.toString().trim()
        val token = DatabaseSettings.token(activity, database)
        if (address.isEmpty()) {
            activity.toast("Type the database address first")
            return
        }
        if (token.isEmpty()) {
            activity.toast("Save the token first")
            return
        }
        // Keep what was typed, so the test and the saved address are the same.
        DatabaseSettings.saveAddress(activity, database, address)
        testResult.text = "Testing..."
        val started = System.currentTimeMillis()
        TursoClient.execute(address, token, "SELECT 1") { result ->
            testResult.text = result.fold(
                { "Connected (${System.currentTimeMillis() - started} ms)" },
                { "Failed: ${it.message}" }
            )
        }
    }
}
