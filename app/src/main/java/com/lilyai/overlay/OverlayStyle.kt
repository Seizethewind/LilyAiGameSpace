package com.lilyai.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.lilyai.ui.Palette
import com.lilyai.ui.dp

// The pink-and-white look of the overlay.

/** The rounded background of the overlay's panels: soft pink fading to white. */
fun Context.panelBackground(): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Palette.pinkSoft, Palette.white)
).apply {
    cornerRadius = dp(20).toFloat()
    setStroke(dp(1), Palette.pink)
}

/** The pink pill the L sits on. */
fun Context.handleBackground(): GradientDrawable = GradientDrawable(
    GradientDrawable.Orientation.LEFT_RIGHT,
    intArrayOf(Palette.pinkDeep, Palette.pink)
).apply { cornerRadius = dp(22).toFloat() }

private fun Context.tileBackground(active: Boolean): GradientDrawable =
    if (active) {
        GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Palette.pinkDeep, Palette.pink)
        ).apply { cornerRadius = dp(14).toFloat() }
    } else {
        GradientDrawable().apply {
            setColor(Palette.white)
            cornerRadius = dp(14).toFloat()
            setStroke(dp(1), Palette.pink)
        }
    }

/** One button of the overlay menu: an icon over a short label. Pink when it is switched on. */
class Tile(context: Context, icon: String, label: String, onClick: () -> Unit) {

    private val iconView = TextView(context).apply {
        text = icon
        textSize = 22f
        gravity = Gravity.CENTER
    }
    private val labelView = TextView(context).apply {
        text = label
        textSize = 11f
        gravity = Gravity.CENTER
        maxLines = 2
        setTextColor(Palette.plum)
    }

    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(context.dp(4), context.dp(8), context.dp(4), context.dp(8))
        background = context.tileBackground(false)
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            .apply { setMargins(context.dp(3), context.dp(3), context.dp(3), context.dp(3)) }
        addView(iconView)
        addView(labelView)
        setOnClickListener { onClick() }
    }

    fun setIcon(icon: String) {
        iconView.text = icon
    }

    fun setLabel(label: String) {
        labelView.text = label
    }

    fun setActive(active: Boolean) {
        view.background = view.context.tileBackground(active)
        labelView.setTextColor(if (active) Palette.white else Palette.plum)
    }
}

/** A row of tiles of equal width. A null leaves an empty space so the others keep their size. */
fun Context.tileRow(vararg tiles: Tile?): LinearLayout {
    val row = LinearLayout(this)
    row.orientation = LinearLayout.HORIZONTAL
    tiles.forEach { tile ->
        val child = tile?.view ?: View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
        row.addView(child)
    }
    return row
}

/** A horizontal strip for swiping between pages. It stops where the finger leaves it, with no fling. */
class PagerScroll(context: Context) : HorizontalScrollView(context) {

    init {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    override fun fling(velocityX: Int) = Unit
}
