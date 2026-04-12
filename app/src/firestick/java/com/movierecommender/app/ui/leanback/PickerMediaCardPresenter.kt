package com.movierecommender.app.ui.leanback

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import com.bumptech.glide.Glide
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.TvShow

class PickerMediaCardPresenter(
    private val isMovieSelected: (Movie) -> Boolean,
    private val isTvSelected: (TvShow) -> Boolean,
    private val isMovieFavorite: (Movie) -> Boolean
) : Presenter() {

    companion object {
        private const val CARD_WIDTH = 260
        private const val CARD_HEIGHT = 390
        private const val POSTER_BASE_URL = "https://image.tmdb.org/t/p/w500"
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
        val cardView = viewHolder.view as ImageCardView

        when (item) {
            is Movie -> bindMovie(cardView, item)
            is TvShow -> bindTvShow(cardView, item)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        Glide.with(cardView.context).clear(cardView.mainImageView)
        cardView.mainImage = null
    }

    private fun bindMovie(cardView: ImageCardView, movie: Movie) {
        val meta = buildList {
            movie.releaseDate?.take(4)?.takeIf { it.isNotBlank() }?.let(::add)
            if (isMovieSelected(movie)) add("Selected")
            if (isMovieFavorite(movie)) add("Favorite")
        }

        cardView.titleText = movie.title
        cardView.contentText = meta.joinToString(" • ")
        cardView.setInfoAreaBackgroundColor(
            when {
                isMovieSelected(movie) -> 0xFF0E4F6E.toInt()
                isMovieFavorite(movie) -> 0xFF5A1D2A.toInt()
                else -> 0xFF1E1E34.toInt()
            }
        )

        loadPoster(cardView, movie.posterPath)
    }

    private fun bindTvShow(cardView: ImageCardView, tvShow: TvShow) {
        val meta = buildList {
            tvShow.firstAirDate?.take(4)?.takeIf { it.isNotBlank() }?.let(::add)
            if (isTvSelected(tvShow)) add("Selected")
            add("Series")
        }

        cardView.titleText = tvShow.name
        cardView.contentText = meta.joinToString(" • ")
        cardView.setInfoAreaBackgroundColor(
            if (isTvSelected(tvShow)) 0xFF0E4F6E.toInt() else 0xFF1E1E34.toInt()
        )

        loadPoster(cardView, tvShow.posterPath)
    }

    private fun loadPoster(cardView: ImageCardView, posterPath: String?) {
        if (posterPath.isNullOrBlank()) {
            cardView.mainImageView.setImageDrawable(ColorDrawable(0xFF263238.toInt()))
            return
        }

        Glide.with(cardView.context)
            .load("$POSTER_BASE_URL$posterPath")
            .centerCrop()
            .placeholder(ColorDrawable(0xFF263238.toInt()))
            .into(cardView.mainImageView)
    }
}