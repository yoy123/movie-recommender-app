package com.movierecommender.app.firestick

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.movierecommender.app.R
import com.movierecommender.app.torrent.TorrentStreamService
import com.movierecommender.app.ui.leanback.BrowseGenreFragment

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_browse_fragment, BrowseGenreFragment())
                .commitNow()
        }
    }

    override fun onDestroy() {
        // Clear torrent cache when app exits
        startService(TorrentStreamService.getClearCacheIntent(this))
        super.onDestroy()
    }
}
