package com.movierecommender.app.ui.screens.firestick

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.webkit.WebView
import kotlin.math.max
import kotlin.math.min

/**
 * TV remote pointer for browser-based trailers.
 *
 * YouTube serves a desktop watch page in WebView; its in-video Skip Ad and
 * playback buttons are mouse/touch controls, not Android DPAD-focus targets.
 * Translate DPAD navigation into a visible pointer and OK into a real touch
 * click on the underlying WebView. Do not launch external apps or skip ads
 * programmatically; the user must explicitly select a visible control.
 */
internal class DpadTrailerWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : WebView(context, attrs) {

    private val density = resources.displayMetrics.density
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pointerX = 0f
    private var pointerY = 0f
    private var isPointerVisible = false

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setWillNotDraw(false)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val supported = when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> true
            else -> false
        }
        if (!supported) return super.dispatchKeyEvent(event)

        if (event.action == KeyEvent.ACTION_DOWN) {
            if (!isPointerVisible) {
                isPointerVisible = true
                // Ad controls on the YouTube desktop player generally appear at
                // the lower right; give DPAD users a nearby starting point.
                pointerX = width * 0.82f
                pointerY = height * 0.72f
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    val newPosition = TrailerPointerMath.move(
                        pointerX, pointerY, width, height, density,
                        event.keyCode, event.repeatCount
                    )
                    pointerX = newPosition.first
                    pointerY = newPosition.second
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> if (event.repeatCount == 0) {
                    tapAtPointer()
                }
            }
            invalidate()
        }
        return true // Also consume corresponding key-up events, not browser shortcuts.
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isPointerVisible) return

        val x = pointerX.coerceIn(0f, width.toFloat())
        val y = pointerY.coerceIn(0f, height.toFloat())
        val r = 15f * density

        // High-contrast cursor is drawn over WebView content, including ads.
        ink.style = Paint.Style.FILL
        ink.color = Color.argb(225, 0, 0, 0)
        canvas.drawCircle(x, y, r + 3f * density, ink)
        ink.color = Color.WHITE
        canvas.drawCircle(x, y, r, ink)
        ink.color = Color.rgb(15, 109, 179)
        canvas.drawCircle(x, y, 5f * density, ink)

        val label = "DPAD: Move pointer     OK: Select     Back: Exit"
        ink.typeface = android.graphics.Typeface.DEFAULT_BOLD
        ink.textSize = 14f * density
        val horizontal = 16f * density
        val baseline = 36f * density
        val textWidth = ink.measureText(label)
        ink.color = Color.argb(205, 0, 0, 0)
        canvas.drawRoundRect(
            horizontal, baseline - 21f * density,
            min(width.toFloat() - horizontal, horizontal + textWidth + 24f * density),
            baseline + 10f * density,
            7f * density, 7f * density, ink
        )
        ink.color = Color.WHITE
        canvas.drawText(label, horizontal + 12f * density, baseline, ink)
    }

    private fun tapAtPointer() {
        // Dispatch real Android touch events to WebView. This works for Skip Ad
        // buttons as well as YouTube player controls and avoids guessing private
        // YouTube DOM selectors that regularly change.
        val time = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, pointerX, pointerY, 0)
        val up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, pointerX, pointerY, 0)
        try {
            super.dispatchTouchEvent(down)
            super.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            up.recycle()
        }
    }
}

/** Pure DPAD position math, separately regression tested. */
internal object TrailerPointerMath {
    fun move(
        x: Float,
        y: Float,
        width: Int,
        height: Int,
        density: Float,
        direction: Int,
        repeatCount: Int
    ): Pair<Float, Float> {
        val margin = 16f * density
        val step = (if (repeatCount >= 5) 86f else 42f) * density
        val dx = when (direction) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -step
            KeyEvent.KEYCODE_DPAD_RIGHT -> step
            else -> 0f
        }
        val dy = when (direction) {
            KeyEvent.KEYCODE_DPAD_UP -> -step
            KeyEvent.KEYCODE_DPAD_DOWN -> step
            else -> 0f
        }
        val maxX = max(0f, width.toFloat() - margin)
        val maxY = max(0f, height.toFloat() - margin)
        return min(maxX, max(0f, x + dx)) to min(maxY, max(0f, y + dy))
    }
}
