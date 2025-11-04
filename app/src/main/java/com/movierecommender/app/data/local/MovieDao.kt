package com.movierecommender.app.data.local

import androidx.room.*
import com.movierecommender.app.data.model.Movie
import kotlinx.coroutines.flow.Flow

@Dao
interface MovieDao {
    
    @Query("SELECT * FROM movies WHERE isSelected = 1 ORDER BY timestamp DESC")
    fun getSelectedMovies(): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE isRecommended = 1 ORDER BY popularity DESC")
    fun getRecommendedMovies(): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE isFavorite = 1 ORDER BY timestamp DESC")
    fun getFavoriteMovies(): Flow<List<Movie>>
    
    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun getMovieById(id: Int): Movie?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: Movie)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<Movie>)
    
    @Update
    suspend fun updateMovie(movie: Movie)
    
    @Delete
    suspend fun deleteMovie(movie: Movie)
    
    @Query("UPDATE movies SET isSelected = 0 WHERE isSelected = 1")
    suspend fun clearSelectedMovies()
    
    @Query("UPDATE movies SET isRecommended = 0 WHERE isRecommended = 1")
    suspend fun clearRecommendedMovies()
    
    @Query("UPDATE movies SET isFavorite = 1 WHERE id = :movieId")
    suspend fun addToFavorites(movieId: Int)
    
    @Query("UPDATE movies SET isFavorite = 0 WHERE id = :movieId")
    suspend fun removeFromFavorites(movieId: Int)
    
    @Query("DELETE FROM movies")
    suspend fun clearAllMovies()
}
