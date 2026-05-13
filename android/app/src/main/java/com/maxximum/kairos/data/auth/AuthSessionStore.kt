package com.maxximum.kairos.data.auth

import android.content.Context
import com.maxximum.kairos.data.remote.AuthTokens
import com.maxximum.kairos.data.remote.AuthUser

class AuthSessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("kairos_auth", Context.MODE_PRIVATE)

    fun save(tokens: AuthTokens) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, tokens.accessToken)
            .putString(KEY_ACCESS_EXPIRES_AT, tokens.accessTokenExpiresAt)
            .putString(KEY_REFRESH_TOKEN, tokens.refreshToken)
            .putString(KEY_REFRESH_EXPIRES_AT, tokens.refreshTokenExpiresAt)
            .putString(KEY_USER_ID, tokens.user.id)
            .putString(KEY_USER_EMAIL, tokens.user.email)
            .apply()
    }

    fun user(): AuthUser? {
        val id = prefs.getString(KEY_USER_ID, null) ?: return null
        val email = prefs.getString(KEY_USER_EMAIL, null) ?: return null
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        if (accessToken.isBlank()) return null
        return AuthUser(id = id, email = email)
    }

    fun accessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_ACCESS_TOKEN = "access_token"
        const val KEY_ACCESS_EXPIRES_AT = "access_expires_at"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_REFRESH_EXPIRES_AT = "refresh_expires_at"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_EMAIL = "user_email"
    }
}
