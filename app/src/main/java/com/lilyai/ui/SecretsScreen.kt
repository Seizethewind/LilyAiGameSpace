package com.lilyai.ui

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lilyai.ai.Provider
import com.lilyai.ai.ProviderSettings
import com.lilyai.data.cloud.DatabaseSettings
import com.lilyai.data.cloud.TursoDatabase

// Everything with a key or a token: the AI providers and the databases. Tap one to set it up.
class SecretsScreen(
    private val activity: Activity,
    private val open: (Screen) -> Unit
) : Screen {

    override val title = "Secrets"

    private val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.label("Pick one to manage its keys or its connection."))
        addView(list)
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    override fun onShow() {
        list.removeAllViews()

        list.addView(activity.heading("AI providers"))
        Provider.values().forEach { provider ->
            val count = ProviderSettings.keys(activity, provider).size
            val status = if (count == 1) "1 key" else "$count keys"
            list.addView(row("${provider.icon}   ${provider.displayName}   -   $status") {
                open(ProviderScreen(activity, provider))
            })
        }

        list.addView(activity.heading("Databases"))
        TursoDatabase.values().forEach { database ->
            val status = if (DatabaseSettings.isReady(activity, database)) "set up" else "not set up"
            list.addView(row("${database.icon}   ${database.displayName}   -   $status") {
                open(TursoScreen(activity, database))
            })
        }
    }

    private fun row(text: String, onClick: () -> Unit): TextView = TextView(activity).apply {
        this.text = text
        textSize = 18f
        setTextColor(Palette.plum)
        setPadding(activity.dp(16), activity.dp(16), activity.dp(16), activity.dp(16))
        background = GradientDrawable().apply {
            setColor(Palette.white)
            cornerRadius = activity.dp(16).toFloat()
            setStroke(activity.dp(1), Palette.pink)
        }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, activity.dp(6), 0, activity.dp(6)) }
        setOnClickListener { onClick() }
    }
}
