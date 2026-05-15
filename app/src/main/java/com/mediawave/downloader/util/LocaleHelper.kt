package com.mediawave.downloader.util

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    const val PREFS_NAME = "mediawave_lang"
    const val KEY_LANGUAGE = "language"

    /**
     * Read the saved language code directly from SharedPreferences.
     * Safe to call from attachBaseContext (before Hilt is ready).
     */
    fun getSavedLang(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LANGUAGE, "system") ?: "system"
    }

    /**
     * Persist the language code to SharedPreferences.
     * Called from ViewModel/Settings so the value survives the next cold start.
     */
    fun saveLang(context: Context, langCode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_LANGUAGE, langCode).apply()
    }

    /**
     * Wraps [base] context with the locale derived from [langCode].
     * Pass "system" to keep the device default.
     */
    fun applyLocale(base: Context, langCode: String): Context {
        if (langCode == "system") return base
        val locale = Locale(langCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
