package com.mediawave.downloader.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        val PREF_QUALITY = stringPreferencesKey("pref_quality")
        val PREF_THEME = intPreferencesKey("pref_theme") // 0=system, 1=light, 2=dark
        val PREF_AUTO_PASTE = booleanPreferencesKey("pref_auto_paste")
        val PREF_ASK_QUALITY = booleanPreferencesKey("pref_ask_quality")
        val PREF_LANGUAGE = stringPreferencesKey("pref_language")
        val PREF_DOWNLOAD_PATH = stringPreferencesKey("pref_download_path")
        val PREF_YTDLP_VERSION = stringPreferencesKey("pref_ytdlp_version")
        val PREF_FIRST_LAUNCH = booleanPreferencesKey("pref_first_launch")
        
        // Supported language codes mapped to their native display names
        val SUPPORTED_LANGUAGES = linkedMapOf(
            "system" to "System Default",
            "en"     to "English",
            "uk"     to "Українська",
            "ru"     to "Русский",
            "de"     to "Deutsch",
            "fr"     to "Français",
            "es"     to "Español",
            "zh"     to "中文",
            "pt"     to "Português",
        )
    }

    val qualityFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PREF_QUALITY] ?: "HD"
    }

    val themeFlow: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[PREF_THEME] ?: 0
    }

    val autoPasteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREF_AUTO_PASTE] ?: true
    }

    val askQualityFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREF_ASK_QUALITY] ?: true
    }

    val languageFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PREF_LANGUAGE] ?: "system"
    }

    val ytdlpVersionFlow: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PREF_YTDLP_VERSION] ?: "Unknown"
    }

    suspend fun setQuality(quality: String) {
        context.dataStore.edit { it[PREF_QUALITY] = quality }
    }

    suspend fun setTheme(theme: Int) {
        context.dataStore.edit { it[PREF_THEME] = theme }
    }

    suspend fun setAutoPaste(enabled: Boolean) {
        context.dataStore.edit { it[PREF_AUTO_PASTE] = enabled }
    }

    suspend fun setAskQuality(enabled: Boolean) {
        context.dataStore.edit { it[PREF_ASK_QUALITY] = enabled }
    }

    suspend fun setLanguage(lang: String) {
        context.dataStore.edit { it[PREF_LANGUAGE] = lang }
    }

    suspend fun setYtdlpVersion(version: String) {
        context.dataStore.edit { it[PREF_YTDLP_VERSION] = version }
    }
    val isFirstLaunchFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[PREF_FIRST_LAUNCH] ?: true
    }

    suspend fun setFirstLaunch(value: Boolean) {
        context.dataStore.edit { it[PREF_FIRST_LAUNCH] = value }
    }
}
