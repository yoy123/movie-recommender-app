package com.movierecommender.app

import android.app.Application
import com.movierecommender.app.data.local.AppDatabase
import com.movierecommender.app.data.remote.TmdbApiService
import com.movierecommender.app.data.settings.SettingsRepository
import com.movierecommender.app.data.repository.MovieRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MovieRecommenderApplication : Application() {
    
    // Application-scoped coroutine scope for background tasks
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    val database by lazy { AppDatabase.getDatabase(this) }
    val settings by lazy { SettingsRepository(this) }
    val repository by lazy { 
        MovieRepository(
            movieDao = database.movieDao(),
            providerContentCrosswalkDao = database.providerContentCrosswalkDao(),
            apiService = TmdbApiService.create(this)  // Pass context for HTTP cache
        ) 
    }
    
    override fun onCreate() {
        super.onCreate()
        
        // Run database cleanup on app startup (non-blocking)
        performDatabaseCleanupIfNeeded()
    }
    
    /**
     * Cleanup orphaned movies from the database if it's been >7 days since last cleanup.
     * Runs asynchronously to avoid blocking app startup.
     */
    private fun performDatabaseCleanupIfNeeded() {
        applicationScope.launch {
            try {
                val lastCleanup = settings.lastDbCleanup.first()
                val (didCleanup, deletedCount) = repository.cleanupOrphanedMoviesIfNeeded(lastCleanup)
                
                if (didCleanup) {
                    settings.setLastDbCleanup(System.currentTimeMillis())
                    android.util.Log.i(
                        "MovieRecommenderApp",
                        "Database cleanup completed: removed $deletedCount orphaned movies"
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MovieRecommenderApp", "Database cleanup failed", e)
            }
        }
    }
}
