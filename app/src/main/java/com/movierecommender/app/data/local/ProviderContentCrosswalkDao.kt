package com.movierecommender.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.movierecommender.app.data.model.ProviderContentCrosswalk

@Dao
interface ProviderContentCrosswalkDao {

    @Query("SELECT * FROM provider_content_crosswalk WHERE tmdbId = :tmdbId AND mediaType = :mediaType")
    suspend fun getCrosswalksForContent(tmdbId: Int, mediaType: String): List<ProviderContentCrosswalk>

    @Query("SELECT * FROM provider_content_crosswalk WHERE tmdbId = :tmdbId AND mediaType = :mediaType AND providerId = :providerId LIMIT 1")
    suspend fun getCrosswalk(tmdbId: Int, mediaType: String, providerId: Int): ProviderContentCrosswalk?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: ProviderContentCrosswalk)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<ProviderContentCrosswalk>)
}
