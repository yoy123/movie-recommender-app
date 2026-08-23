package com.movierecommender.app.data.model

/** A progressive snapshot of Watch Options while all configured sources continue searching. */
data class WatchOptionsLoadState(
    val options: List<WatchOption>,
    val isLoading: Boolean
)