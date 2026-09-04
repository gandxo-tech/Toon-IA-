package com.example.util

import android.content.Context
import com.example.BuildConfig

object ApiKeyManager {
    private const val PREF_NAME = "toonia_prefs"
    private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun getApiKey(context: Context? = null): String {
        val ctx = context ?: appContext ?: return ""
        val prefs = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedKey = prefs.getString(KEY_GEMINI_API_KEY, null)
        if (!savedKey.isNullOrBlank()) {
            return savedKey
        }
        val buildConfigKey = BuildConfig.GEMINI_API_KEY
        if (!buildConfigKey.isNullOrBlank() && buildConfigKey != "MY_GEMINI_API_KEY") {
            return buildConfigKey
        }
        return ""
    }

    fun saveApiKey(context: Context, apiKey: String) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_GEMINI_API_KEY, apiKey.trim()).apply()
    }
}
