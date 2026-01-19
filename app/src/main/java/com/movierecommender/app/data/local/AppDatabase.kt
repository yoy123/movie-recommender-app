package com.movierecommender.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.movierecommender.app.data.model.Movie

@Database(
    entities = [Movie::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun movieDao(): MovieDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        /**
         * Migration from version 1 to version 2.
         * This migration preserves all existing data.
         * Add actual schema changes here when needed.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Version 1 -> 2: No schema changes needed, this is a placeholder
                // for the original destructive migration that happened.
                // Future migrations should add actual ALTER TABLE statements here.
                android.util.Log.i("AppDatabase", "Migration 1->2 completed (no schema changes)")
            }
        }
        
        /**
         * Template for future migrations. Copy and modify as needed:
         * 
         * val MIGRATION_2_3 = object : Migration(2, 3) {
         *     override fun migrate(db: SupportSQLiteDatabase) {
         *         // Example: Add new column with default value
         *         db.execSQL("ALTER TABLE movies ADD COLUMN newColumn TEXT DEFAULT ''")
         *     }
         * }
         */
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "movie_recommender_database"
                )
                    // Add all migrations here to preserve user data on upgrades
                    .addMigrations(MIGRATION_1_2)
                    // REMOVED: .fallbackToDestructiveMigration() - This was deleting all user favorites!
                    // If a migration is missing, the app will crash on startup with a clear error
                    // rather than silently deleting user data. This is the safer behavior.
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
