package com.lilyai.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout

// Will keep the app's memory: saved chats and the Auto history.
class RecordsScreen(context: Context) : Screen {

    override val title = "Records"

    private val root = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(16), context.dp(8), context.dp(16), context.dp(16))
        addView(context.heading("Records"))
        addView(context.label("Your saved chats and Auto history will be kept here. Coming soon."))
    }

    override fun view(): View = root
}
