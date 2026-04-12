package com.movierecommender.app.firestick

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.movierecommender.app.R
import com.movierecommender.app.ui.leanback.LeanbackPickerFragment

class LeanbackPickerActivity : FragmentActivity() {

    companion object {
        const val EXTRA_GENRE_ID = "extra_genre_id"
        const val EXTRA_GENRE_NAME = "extra_genre_name"
        const val EXTRA_CONTENT_MODE = "extra_content_mode"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            val fragment = LeanbackPickerFragment.newInstance(
                genreId = intent.getIntExtra(EXTRA_GENRE_ID, -1),
                genreName = intent.getStringExtra(EXTRA_GENRE_NAME).orEmpty(),
                contentModeName = intent.getStringExtra(EXTRA_CONTENT_MODE).orEmpty()
            )

            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, fragment)
                .commitNow()
        }
    }
}