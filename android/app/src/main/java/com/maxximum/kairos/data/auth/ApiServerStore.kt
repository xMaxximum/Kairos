package com.maxximum.kairos.data.auth

import android.content.Context
import com.maxximum.kairos.BuildConfig

class ApiServerStore(context: Context) {
    private val prefs = context.getSharedPreferences("kairos_api_server", Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        return prefs.getString(KEY_BASE_URL, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.API_BASE_URL
    }

    fun setBaseUrl(baseUrl: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
            .apply()
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim()
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
    }
}
