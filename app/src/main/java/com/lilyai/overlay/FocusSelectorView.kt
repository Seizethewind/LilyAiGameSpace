package com.lilyai.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import com.lilyai.ui.Palette
import com.lilyai.ui.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A full-screen layer for drawing one box over whatever is on screen.
// Drag on an empty spot to draw a box, drag inside it to move it, drag a corner to resize it.
class FocusSelectorView(context: Context) : View(context) {

    private enum class Mode { NONE, DRAW, MOVE, RESIZE }

    private var box: RectF? = null
    private var before: RectF? = null
    private var mode = Mode.NONE
    private var anchorX = 0f
    private var anchorY = 0f
    private var dragLeft = false
    private var dragRight = false
    private var dragTop = false
    private var dragBottom = false

    private val grab = context.dp(32).toFloat()
    private val minSize = context.dp(48).toFloat()
    private val handleRadius = context.dp(10).toFloat()

    private val dim = Paint().apply { color = 0x88000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.pink
        style = Paint.Style.STROKE
        strokeWidth = context.dp(3).toFloat()
    }
    private val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Palette.white }
    private val handleEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.pinkDeep
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2).toFloat()
    }

    /** The box in screen pixels, or null if none is drawn. */
    fun screenRect(): Rect? {
        val r = box ?: return null
        val location = IntArray(2)
        getLocationOnScreen(location)
        return Rect(
            (r.left + location[0]).roundToInt(),
            (r.top + location[1]).roundToInt(),
            (r.right + location[0]).roundToInt(),
            (r.bottom + location[1]).roundToInt()
        )
    }

    /** Shows a box given in screen pixels, for example the one saved earlier. */
    fun setScreenRect(rect: Rect) {
        post {
            val location = IntArray(2)
            getLocationOnScreen(location)
            box = RectF(
                (rect.left - location[0]).toFloat(),
                (rect.top - location[1]).toFloat(),
                (rect.right - location[0]).toFloat(),
                (rect.bottom - location[1]).toFloat()
            )
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = box
        if (r == null) {
            canvas.drawRect(0f, 0f, w, h, dim)
            return
        }
        // Dim everything except the box, so the game shows through clearly inside it.
        canvas.drawRect(0f, 0f, w, r.top, dim)
        canvas.drawRect(0f, r.bottom, w, h, dim)
        canvas.drawRect(0f, r.top, r.left, r.bottom, dim)
        canvas.drawRect(r.right, r.top, w, r.bottom, dim)
        canvas.drawRect(r, border)
        listOf(
            r.left to r.top,
            r.right to r.top,
            r.left to r.bottom,
            r.right to r.bottom
        ).forEach { (cx, cy) ->
            canvas.drawCircle(cx, cy, handleRadius, handleFill)
            canvas.drawCircle(cx, cy, handleRadius, handleEdge)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x.coerceIn(0f, width.toFloat())
        val y = event.y.coerceIn(0f, height.toFloat())
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(x, y)
            MotionEvent.ACTION_MOVE -> drag(x, y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finish()
        }
        invalidate()
        return true
    }

    private fun start(x: Float, y: Float) {
        val r = box
        if (r != null) {
            val nearLeft = abs(x - r.left) < grab
            val nearRight = abs(x - r.right) < grab
            val nearTop = abs(y - r.top) < grab
            val nearBottom = abs(y - r.bottom) < grab
            if ((nearLeft || nearRight) && (nearTop || nearBottom)) {
                mode = Mode.RESIZE
                dragLeft = nearLeft
                dragRight = !nearLeft && nearRight
                dragTop = nearTop
                dragBottom = !nearTop && nearBottom
                return
            }
            if (r.contains(x, y)) {
                mode = Mode.MOVE
                anchorX = x
                anchorY = y
                return
            }
        }
        mode = Mode.DRAW
        before = r?.let { RectF(it) }
        anchorX = x
        anchorY = y
        box = RectF(x, y, x, y)
    }

    private fun drag(x: Float, y: Float) {
        val r = box ?: return
        when (mode) {
            Mode.DRAW -> box = RectF(min(anchorX, x), min(anchorY, y), max(anchorX, x), max(anchorY, y))
            Mode.MOVE -> {
                val dx = (x - anchorX).coerceIn(-r.left, width - r.right)
                val dy = (y - anchorY).coerceIn(-r.top, height - r.bottom)
                r.offset(dx, dy)
                anchorX = x
                anchorY = y
            }
            Mode.RESIZE -> {
                if (dragLeft) r.left = min(x, r.right - minSize)
                if (dragRight) r.right = max(x, r.left + minSize)
                if (dragTop) r.top = min(y, r.bottom - minSize)
                if (dragBottom) r.bottom = max(y, r.top + minSize)
            }
            Mode.NONE -> Unit
        }
    }

    // A box that is too small was probably an accidental tap, so the earlier box comes back.
    private fun finish() {
        val r = box
        if (mode == Mode.DRAW && r != null && (r.width() < minSize || r.height() < minSize)) {
            box = before
        }
        mode = Mode.NONE
    }
}
