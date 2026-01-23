package com.movierecommender.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_NAME = "user_settings"

private val Context.dataStore by preferencesDataStore(name = SETTINGS_NAME)

class SettingsRepository(private val context: Context) {

    object Keys {
        val USER_NAME = stringPreferencesKey("user_name")
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        
        // LLM Consent - GDPR/CCPA compliance
        // When true, user has consented to sending movie selections to OpenAI
        // When false, app uses TMDB-only fallback recommendations
        val LLM_CONSENT_GIVEN = booleanPreferencesKey("llm_consent_given")
        val LLM_CONSENT_ASKED = booleanPreferencesKey("llm_consent_asked")
        
        // Database cleanup - last time orphaned movies were cleaned
        val LAST_DB_CLEANUP = longPreferencesKey("last_db_cleanup")
        
        val INDIE_PREFERENCE = floatPreferencesKey("indie_preference")
        val USE_INDIE = booleanPreferencesKey("use_indie")
        val POPULARITY_PREFERENCE = floatPreferencesKey("popularity_preference")
        val USE_POPULARITY = booleanPreferencesKey("use_popularity")
        val RELEASE_YEAR_START = floatPreferencesKey("release_year_start")
        val RELEASE_YEAR_END = floatPreferencesKey("release_year_end")
        val USE_RELEASE_YEAR = booleanPreferencesKey("use_release_year")
        val TONE_PREFERENCE = floatPreferencesKey("tone_preference")
        val USE_TONE = booleanPreferencesKey("use_tone")
        val INTERNATIONAL_PREFERENCE = floatPreferencesKey("international_preference")
        val USE_INTERNATIONAL = booleanPreferencesKey("use_international")
        val EXPERIMENTAL_PREFERENCE = floatPreferencesKey("experimental_preference")
        val USE_EXPERIMENTAL = booleanPreferencesKey("use_experimental")
    }

    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME] ?: "" }
    val isFirstRun: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_FIRST_RUN] ?: true }
    val darkMode: Flow<Boolean> = context.dataStore.data.map { it[Keys.DARK_MODE] ?: true }
    
    // LLM consent flows - false by default (opt-in required for GDPR)
    val llmConsentGiven: Flow<Boolean> = context.dataStore.data.map { it[Keys.LLM_CONSENT_GIVEN] ?: false }
    val llmConsentAsked: Flow<Boolean> = context.dataStore.data.map { it[Keys.LLM_CONSENT_ASKED] ?: false }
    
    // Database cleanup - 0 means never cleaned
    val lastDbCleanup: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_DB_CLEANUP] ?: 0L }

    val indiePreference: Flow<Float> = context.dataStore.data.map { it[Keys.INDIE_PREFERENCE] ?: 0.5f }
    val useIndie: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_INDIE] ?: true }
    val popularityPreference: Flow<Float> = context.dataStore.data.map { it[Keys.POPULARITY_PREFERENCE] ?: 0.5f }
    val usePopularity: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_POPULARITY] ?: true }
    val releaseYearStart: Flow<Float> = context.dataStore.data.map { it[Keys.RELEASE_YEAR_START] ?: 1950f }
    val releaseYearEnd: Flow<Float> = context.dataStore.data.map { it[Keys.RELEASE_YEAR_END] ?: 2026f }
    val useReleaseYear: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_RELEASE_YEAR] ?: true }
    val tonePreference: Flow<Float> = context.dataStore.data.map { it[Keys.TONE_PREFERENCE] ?: 0.5f }
    val useTone: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_TONE] ?: true }
    val internationalPreference: Flow<Float> = context.dataStore.data.map { it[Keys.INTERNATIONAL_PREFERENCE] ?: 0.5f }
    val useInternational: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_INTERNATIONAL] ?: true }
    val experimentalPreference: Flow<Float> = context.dataStore.data.map { it[Keys.EXPERIMENTAL_PREFERENCE] ?: 0.5f }
    val useExperimental: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_EXPERIMENTAL] ?: true }

    companion object {
        /** Maximum allowed length for user name */
        const val MAX_USER_NAME_LENGTH = 50
        
        /**
         * Sanitize user name input:
         * - Trim leading/trailing whitespace
         * - Collapse multiple spaces to single space
         * - Limit to MAX_USER_NAME_LENGTH characters
         * - Remove control characters and most special characters (allow letters, numbers, spaces, apostrophe, hyphen)
         */
        fun sanitizeUserName(input: String): String {
            return input
                .trim()
                .replace(Regex("\\s+"), " ") // collapse multiple spaces
                .replace(Regex("[^\\p{L}\\p{N} '-]"), "") // only allow letters, numbers, space, apostrophe, hyphen
                .take(MAX_USER_NAME_LENGTH)
        }
    }

    /**
     * Set user name with validation and sanitization.
     * @param name The raw user input
     * @return The sanitized name that was actually stored
     */
    suspend fun setUserName(name: String): String {
        val sanitized = sanitizeUserName(name)
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = sanitized
        }
        return sanitized
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.IS_FIRST_RUN] = false }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = isDark }
    }
    
    /**
     * Record user's LLM consent decision.
     * @param consented true if user accepts sending data to OpenAI, false if declined
     */
    suspend fun setLlmConsent(consented: Boolean) {
        context.dataStore.edit { 
            it[Keys.LLM_CONSENT_GIVEN] = consented
            it[Keys.LLM_CONSENT_ASKED] = true
        }
    }

    suspend fun setIndiePreference(value: Float) {
        context.dataStore.edit { it[Keys.INDIE_PREFERENCE] = value }
    }

    suspend fun setUseIndie(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_INDIE] = value }
    }

    suspend fun setPopularityPreference(value: Float) {
        context.dataStore.edit { it[Keys.POPULARITY_PREFERENCE] = value }
    }

    suspend fun setUsePopularity(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_POPULARITY] = value }
    }

    suspend fun setReleaseYearStart(value: Float) {
        context.dataStore.edit { it[Keys.RELEASE_YEAR_START] = value }
    }

    suspend fun setReleaseYearEnd(value: Float) {
        context.dataStore.edit { it[Keys.RELEASE_YEAR_END] = value }
    }

    suspend fun setUseReleaseYear(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_RELEASE_YEAR] = value }
    }

    suspend fun setTonePreference(value: Float) {
        context.dataStore.edit { it[Keys.TONE_PREFERENCE] = value }
    }

    suspend fun setUseTone(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_TONE] = value }
    }

    suspend fun setInternationalPreference(value: Float) {
        context.dataStore.edit { it[Keys.INTERNATIONAL_PREFERENCE] = value }
    }

    suspend fun setUseInternational(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_INTERNATIONAL] = value }
    }

    suspend fun setExperimentalPreference(value: Float) {
        context.dataStore.edit { it[Keys.EXPERIMENTAL_PREFERENCE] = value }
    }

    suspend fun setUseExperimental(value: Boolean) {
        context.dataStore.edit { it[Keys.USE_EXPERIMENTAL] = value }
    }
    
    /**
     * Record when database cleanup was last performed.
     */
    suspend fun setLastDbCleanup(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_DB_CLEANUP] = timestamp }
    }
}
