package com.example.cosmos

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

object LocaleHelper {

    private const val PREFS = "cosmos_locale"
    private const val KEY   = "language"

    /** Devuelve el código guardado: "es", "en", "eu" o "" (sistema). */
    fun getSavedLanguage(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
    }

    /** Guarda el código de idioma en SharedPreferences. */
    fun saveLanguage(context: Context, lang: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, lang).apply()
    }

    /**
     * Aplica el locale guardado al contexto.
     * Llamar desde attachBaseContext de Activity y Application.
     */
    fun applyLocale(base: Context): Context {
        val lang = getSavedLanguage(base)
        if (lang.isEmpty()) return base  // idioma del sistema
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
