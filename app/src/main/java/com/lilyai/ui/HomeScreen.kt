package com.lilyai.ui

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

// The front page: just the name.
class HomeScreen(context: Context) : Screen {

    override val title = "LilyAi"

    private val root = FrameLayout(context).apply {
        addView(
            TextView(context).apply {
                text = "LilyAi"
                textSize = 44f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Palette.pinkDeep)
            },
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        )
    }

    override fun view(): View = root
}
