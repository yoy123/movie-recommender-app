package com.movierecommender.app.ui.leanback

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter

data class PickerActionItem(
    val id: String,
    val title: String,
    val description: String,
    val accentColor: Int
)

class PickerActionCardPresenter : Presenter() {

    companion object {
        private const val CARD_WIDTH = 280
        private const val CARD_HEIGHT = 158
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
        val action = item as PickerActionItem
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = action.title
        cardView.contentText = action.description

        val gradient = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(action.accentColor, darkenColor(action.accentColor, 0.58f))
        ).apply {
            cornerRadius = 12f
        }

        cardView.mainImageView.setImageDrawable(gradient)
        cardView.mainImageView.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        (viewHolder.view as ImageCardView).mainImage = null
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = ((color shr 16 and 0xFF) * factor).toInt()
        val g = ((color shr 8 and 0xFF) * factor).toInt()
        val b = ((color and 0xFF) * factor).toInt()
        return Color.argb(255, r, g, b)
    }
}