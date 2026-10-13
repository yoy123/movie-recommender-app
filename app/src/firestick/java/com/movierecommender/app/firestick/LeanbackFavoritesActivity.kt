package com.movierecommender.app.firestick

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.movierecommender.app.R
import com.movierecommender.app.ui.leanback.LeanbackFavoritesFragment

/** Favorites must use the same native Leanback Browse UI/theme as the front menu. */
class LeanbackFavoritesActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, LeanbackFavoritesFragment())
                .commitNow()
        }
    }
}
