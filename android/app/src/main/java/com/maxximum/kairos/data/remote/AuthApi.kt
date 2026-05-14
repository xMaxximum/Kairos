package com.maxximum.kairos.data.remote

import com.maxximum.kairos.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class AuthUser(
    val id: String,
    val email: String
)

data class AuthTokens(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: AuthUser
)

class AuthApi(
    private val baseUrlProvider: () -> String = { BuildConfig.API_BASE_URL }
) {
    suspend fun register(email: String, password: String, deviceName: String): AuthTokens {
        return authRequest(
            path = "api/auth/register",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("deviceName", deviceName)
        )
    }

    suspend fun login(email: String, password: String, deviceName: String): AuthTokens {
        return authRequest(
            path = "api/auth/login",
            body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("deviceName", deviceName)
        )
    }

    suspend fun refresh(refreshToken: String, deviceName: String): AuthTokens {
        return authRequest(
            path = "api/auth/refresh",
            body = JSONObject()
                .put("refreshToken", refreshToken)
                .put("deviceName", deviceName)
        )
    }

    suspend fun me(accessToken: String): AuthUser = withContext(Dispatchers.IO) {
        val connection = openConnection("api/auth/me").apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val response = readJsonResponse(connection)
        AuthUser(
            id = response.getString("id"),
            email = response.getString("email")
        )
    }

    private suspend fun authRequest(path: String, body: JSONObject): AuthTokens = withContext(Dispatchers.IO) {
        val connection = openConnection(path).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { output ->
            output.write(body.toString().toByteArray(Charsets.UTF_8))
        }
        val response = readJsonResponse(connection)
        val user = response.getJSONObject("user")
        AuthTokens(
            accessToken = response.getString("accessToken"),
            accessTokenExpiresAt = response.getString("accessTokenExpiresAt"),
            refreshToken = response.getString("refreshToken"),
            refreshTokenExpiresAt = response.getString("refreshTokenExpiresAt"),
            user = AuthUser(
                id = user.getString("id"),
                email = user.getString("email")
            )
        )
    }

    private fun openConnection(path: String): HttpURLConnection {
        val baseUrl = baseUrlProvider()
        val url = URL(baseUrl.trimEnd('/') + "/" + path.trimStart('/'))
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readJsonResponse(connection: HttpURLConnection): JSONObject {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }.orEmpty()

        if (status !in 200..299) {
            val message = text.takeIf { it.isNotBlank() } ?: "HTTP $status"
            throw AuthApiException(message)
        }

        return JSONObject(text)
    }
}

class AuthApiException(message: String) : Exception(message)
