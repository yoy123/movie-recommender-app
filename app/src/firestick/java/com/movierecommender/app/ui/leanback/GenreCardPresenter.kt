package com.movierecommender.app.ui.leanback

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.movierecommender.app.data.model.Genre

class GenreCardPresenter : Presenter() {

    companion object {
        private const val CARD_WIDTH = 313
        private const val CARD_HEIGHT = 176

        private val GENRE_COLORS = intArrayOf(
            0xFF1B5E20.toInt(), // Dark Green
            0xFF0D47A1.toInt(), // Dark Blue
            0xFF4A148C.toInt(), // Deep Purple
            0xFFB71C1C.toInt(), // Dark Red
            0xFFE65100.toInt(), // Deep Orange
            0xFF006064.toInt(), // Dark Teal
            0xFF263238.toInt(), // Blue Grey 900
            0xFF880E4F.toInt(), // Pink 900
            0xFF1A237E.toInt(), // Indigo 900
            0xFF33691E.toInt(), // Light Green 900
            0xFF3E2723.toInt(), // Brown 900
            0xFF01579B.toInt(), // Light Blue 900
            0xFFF57F17.toInt(), // Yellow 900
            0xFF311B92.toInt(), // Deep Purple 900
            0xFF827717.toInt(), // Lime 900
            0xFF004D40.toInt(), // Teal 900
            0xFFBF360C.toInt(), // Deep Orange 900
            0xFF1B5E20.toInt(), // Green 900
            0xFF0D47A1.toInt(), // Blue 900
            0xFF4A148C.toInt(), // Purple 900
        )
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView = ImageCardView(parent.context).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
        }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, item: Any) {
        val genre = item as Genre
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = genre.name
        cardView.contentText = ""

        // Use floorMod to avoid Math.abs(Int.MIN_VALUE) overflow producing a negative index.
        val colorIndex = Math.floorMod(genre.id, GENRE_COLORS.size)
        val baseColor = GENRE_COLORS[colorIndex]
        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(baseColor, darkenColor(baseColor, 0.6f))
        )
        gradient.cornerRadius = 8f
        cardView.mainImageView.setImageDrawable(gradient)

        // Overlay the genre name on the card image
        cardView.mainImageView.let { iv ->
            iv.scaleType = android.widget.ImageView.ScaleType.CENTER
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return Color.argb(255, r, g, b)
    }
}
