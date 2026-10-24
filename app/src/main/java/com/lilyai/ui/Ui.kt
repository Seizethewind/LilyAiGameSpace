package com.lilyai.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// The pink-and-white look of the app. Dark plum text on the light parts, white text on deep pink.
object Palette {
    val pinkDeep = 0xFFE0408A.toInt()
    val pink = 0xFFFF7EB6.toInt()
    val pinkSoft = 0xFFFFD9EA.toInt()
    val white = 0xFFFFFFFF.toInt()
    val plum = 0xFF4A1942.toInt()
    val plumSoft = 0xFF9A7A92.toInt()
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

/** Soft pink at the top fading to white at the bottom. */
fun Context.softGradient() = GradientDrawable(
    GradientDrawable.Orientation.TOP_BOTTOM,
    intArrayOf(Palette.pinkSoft, Palette.white)
)

fun Context.toast(message: String) =
    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()

private fun Context.rowParams() = LinearLayout.LayoutParams(
    ViewGroup.LayoutParams.MATCH_PARENT,
    ViewGroup.LayoutParams.WRAP_CONTENT
).apply { setMargins(0, dp(6), 0, dp(6)) }

fun Context.heading(text: String) = TextView(this).apply {
    this.text = text
    textSize = 20f
    setTypeface(typeface, Typeface.BOLD)
    setTextColor(Palette.plum)
    layoutParams = rowParams()
}

fun Context.label(text: String) = TextView(this).apply {
    this.text = text
    textSize = 14f
    setTextColor(Palette.plum)
    layoutParams = rowParams()
}

/** A rounded pink button. Only use it inside a vertical LinearLayout. */
fun Context.pinkButton(label: String, onClick: () -> Unit) = Button(this).apply {
    text = label
    isAllCaps = false
    setTextColor(Palette.white)
    background = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(Palette.pinkDeep, Palette.pink)
    ).apply { cornerRadius = dp(24).toFloat() }
    setOnClickListener { onClick() }
    layoutParams = rowParams()
}

/** A rounded white text box with a pink outline. Only use it inside a vertical LinearLayout. */
fun Context.input(hint: String, password: Boolean = false, multiline: Boolean = false) =
    EditText(this).apply {
        this.hint = hint
        setTextColor(Palette.plum)
        setHintTextColor(Palette.plumSoft)
        // setSingleLine replaces the text transformation, so the password type must come after it.
        if (!multiline) setSingleLine()
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = GradientDrawable().apply {
            setColor(Palette.white)
            cornerRadius = dp(12).toFloat()
            setStroke(dp(1), Palette.pink)
        }
        setPadding(dp(12), dp(10), dp(12), dp(10))
        layoutParams = rowParams()
    }
