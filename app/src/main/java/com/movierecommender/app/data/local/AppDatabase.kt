package com.movierecommender.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.movierecommender.app.data.model.Movie
import com.movierecommender.app.data.model.ProviderContentCrosswalk

@Database(
    entities = [Movie::class, ProviderContentCrosswalk::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun movieDao(): MovieDao
    abstract fun providerContentCrosswalkDao(): ProviderContentCrosswalkDao
    
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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `provider_content_crosswalk` (
                        `tmdbId` INTEGER NOT NULL,
                        `mediaType` TEXT NOT NULL,
                        `providerId` INTEGER NOT NULL,
                        `providerKey` TEXT NOT NULL,
                        `providerContentId` TEXT NOT NULL,
                        `canonicalUrl` TEXT,
                        `appDeepLink` TEXT,
                        `source` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `lastVerifiedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`tmdbId`, `mediaType`, `providerId`)
                    )
                    """.trimIndent()
                )
                android.util.Log.i("AppDatabase", "Migration 2->3 completed (added provider_content_crosswalk)")
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
                    .addMigrations(MIGRATION_2_3)
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
