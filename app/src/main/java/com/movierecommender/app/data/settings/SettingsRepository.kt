package com.movierecommender.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
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

    val indiePreference: Flow<Float> = context.dataStore.data.map { it[Keys.INDIE_PREFERENCE] ?: 0.5f }
    val useIndie: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_INDIE] ?: true }
    val popularityPreference: Flow<Float> = context.dataStore.data.map { it[Keys.POPULARITY_PREFERENCE] ?: 0.5f }
    val usePopularity: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_POPULARITY] ?: true }
    val releaseYearStart: Flow<Float> = context.dataStore.data.map { it[Keys.RELEASE_YEAR_START] ?: 1950f }
    val releaseYearEnd: Flow<Float> = context.dataStore.data.map { it[Keys.RELEASE_YEAR_END] ?: 2025f }
    val useReleaseYear: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_RELEASE_YEAR] ?: true }
    val tonePreference: Flow<Float> = context.dataStore.data.map { it[Keys.TONE_PREFERENCE] ?: 0.5f }
    val useTone: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_TONE] ?: true }
    val internationalPreference: Flow<Float> = context.dataStore.data.map { it[Keys.INTERNATIONAL_PREFERENCE] ?: 0.5f }
    val useInternational: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_INTERNATIONAL] ?: true }
    val experimentalPreference: Flow<Float> = context.dataStore.data.map { it[Keys.EXPERIMENTAL_PREFERENCE] ?: 0.5f }
    val useExperimental: Flow<Boolean> = context.dataStore.data.map { it[Keys.USE_EXPERIMENTAL] ?: true }

    suspend fun setUserName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.USER_NAME] = name
        }
    }

    suspend fun setFirstRunDone() {
        context.dataStore.edit { it[Keys.IS_FIRST_RUN] = false }
    }

    suspend fun setDarkMode(isDark: Boolean) {
        context.dataStore.edit { it[Keys.DARK_MODE] = isDark }
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
}
