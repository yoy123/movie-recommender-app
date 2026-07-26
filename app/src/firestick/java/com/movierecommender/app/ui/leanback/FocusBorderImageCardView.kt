package com.movierecommender.app.ui.leanback

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.leanback.widget.ImageCardView

class FocusBorderImageCardView(context: Context) : ImageCardView(context) {

    companion object {
        private const val BORDER_ANIMATION_MS = 1_100L
        private const val BORDER_COLOR_START = 0xFF00BCD4.toInt()
        private const val BORDER_COLOR_END = 0xFFF2C14E.toInt()
    }

    private val borderWidth = (2f * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    private val borderRadius = 4f * resources.displayMetrics.density
    private val borderDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(Color.TRANSPARENT)
        cornerRadius = borderRadius
        setStroke(borderWidth, Color.TRANSPARENT)
    }
    private val borderAnimator = ValueAnimator.ofObject(
        ArgbEvaluator(),
        BORDER_COLOR_START,
        BORDER_COLOR_END
    ).apply {
        duration = BORDER_ANIMATION_MS
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animator ->
            borderDrawable.setStroke(borderWidth, animator.animatedValue as Int)
        }
    }

    private val infoField: View?
        get() = findViewById(androidx.leanback.R.id.info_field)

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        infoField?.foreground = borderDrawable
        if (gainFocus) {
            borderAnimator.start()
        } else {
            borderAnimator.cancel()
            borderDrawable.setStroke(borderWidth, Color.TRANSPARENT)
        }
    }

    override fun onDetachedFromWindow() {
        borderAnimator.cancel()
        super.onDetachedFromWindow()
    }
}