package com.lilyai.ui

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.GradientDrawable
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.lilyai.ai.Provider
import com.lilyai.ai.ProviderSettings

// The list of providers. Tap one to manage its keys and model.
class SecretsScreen(
    private val activity: Activity,
    private val open: (Screen) -> Unit
) : Screen {

    override val title = "Secrets"

    private val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }

    private val root = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(activity.dp(16), activity.dp(8), activity.dp(16), activity.dp(16))
        addView(activity.label("Pick a provider to manage its API keys and model."))
        addView(list)
    }

    private val scroll = ScrollView(activity).apply { addView(root) }

    override fun view(): View = scroll

    override fun onShow() {
        list.removeAllViews()
        Provider.values().forEach { list.addView(row(it)) }
    }

    private fun row(provider: Provider): TextView {
        val count = ProviderSettings.keys(activity, provider).size
        val keysText = if (count == 1) "1 key" else "$count keys"
        return TextView(activity).apply {
            text = "${provider.icon}   ${provider.displayName}   -   $keysText"
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
            setOnClickListener { open(ProviderScreen(activity, provider)) }
        }
    }
}
