package com.movierecommender.app

import android.app.Application
import com.movierecommender.app.data.local.AppDatabase
import com.movierecommender.app.data.remote.TmdbApiService
import com.movierecommender.app.data.settings.SettingsRepository
import com.movierecommender.app.data.repository.MovieRepository

class MovieRecommenderApplication : Application() {
    
    val database by lazy { AppDatabase.getDatabase(this) }
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { 
        MovieRepository(
            movieDao = database.movieDao(),
            apiService = TmdbApiService.create()
        ) 
    }
    
    override fun onCreate() {
        super.onCreate()
    }
}
