package com.maxximum.kairos.data.remote

import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.OffsetDateTime

data class RemoteTask(
    val id: String,
    val clientId: String,
    val title: String,
    val description: String,
    val createdAt: String,
    val updatedAt: String,
    val deletedAt: String?,
    val reminderTime: String?,
    val recurrence: String,
    val isHighPriority: Boolean,
    val isFullScreenReminder: Boolean,
    val attachments: List<String>,
    val isCompleted: Boolean,
    val isArchived: Boolean,
    val isOneOffTask: Boolean
) {
    fun toTodo(): Todo {
        return Todo(
            clientId = clientId,
            serverId = id,
            title = title,
            description = description,
            timestamp = parseInstantMillis(createdAt) ?: System.currentTimeMillis(),
            updatedAt = parseInstantMillis(updatedAt) ?: System.currentTimeMillis(),
            deletedAt = deletedAt?.let(::parseInstantMillis),
            lastSyncedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.SYNCED.name,
            reminderTime = reminderTime?.let(::parseInstantMillis),
            recurrence = recurrence,
            isHighPriority = isHighPriority,
            isFullScreenReminder = isFullScreenReminder,
            attachments = attachments,
            isCompleted = isCompleted,
            isArchived = isArchived,
            isOneOffTask = isOneOffTask
        )
    }
}

class TaskApi(
    private val baseUrlProvider: () -> String
) {
    suspend fun getTasks(accessToken: String): List<RemoteTask> = withContext(Dispatchers.IO) {
        val connection = openConnection("api/tasks?includeDeleted=true").apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val response = readTextResponse(connection)
        val array = JSONArray(response)
        buildList {
            for (i in 0 until array.length()) {
                add(array.getJSONObject(i).toRemoteTask())
            }
        }
    }

    suspend fun upsertTask(accessToken: String, todo: Todo): RemoteTask = withContext(Dispatchers.IO) {
        val connection = openConnection("api/tasks").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { output ->
            output.write(todo.toCreateJson().toString().toByteArray(Charsets.UTF_8))
        }
        JSONObject(readTextResponse(connection)).toRemoteTask()
    }

    suspend fun deleteTaskByClientId(accessToken: String, clientId: String) = withContext(Dispatchers.IO) {
        val connection = openConnection("api/tasks/client/$clientId").apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        readTextResponse(connection, allowEmpty = true, treatNotFoundAsSuccess = true)
    }

    private fun openConnection(path: String): HttpURLConnection {
        val url = URL(baseUrlProvider().trimEnd('/') + "/" + path.trimStart('/'))
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readTextResponse(
        connection: HttpURLConnection,
        allowEmpty: Boolean = false,
        treatNotFoundAsSuccess: Boolean = false
    ): String {
        val status = connection.responseCode
        if (treatNotFoundAsSuccess && status == HttpURLConnection.HTTP_NOT_FOUND) {
            return ""
        }
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        }.orEmpty()

        if (status !in 200..299) {
            throw TaskApiException(text.takeIf { it.isNotBlank() } ?: "HTTP $status")
        }
        if (!allowEmpty && text.isBlank()) {
            throw TaskApiException("Empty response")
        }
        return text
    }
}

class TaskApiException(message: String) : Exception(message)

private fun Todo.toCreateJson(): JSONObject {
    return JSONObject()
        .put("clientId", clientId)
        .put("title", title)
        .put("description", description)
        .put("reminderTime", reminderTime?.let { Instant.ofEpochMilli(it).toString() })
        .put("recurrence", recurrence)
        .put("isHighPriority", isHighPriority)
        .put("isFullScreenReminder", isFullScreenReminder)
        .put("attachments", JSONArray(attachments))
        .put("isCompleted", isCompleted)
        .put("isArchived", isArchived)
        .put("isOneOffTask", isOneOffTask)
}

private fun JSONObject.toRemoteTask(): RemoteTask {
    return RemoteTask(
        id = getString("id"),
        clientId = getString("clientId"),
        title = getString("title"),
        description = optString("description"),
        createdAt = getString("createdAt"),
        updatedAt = getString("updatedAt"),
        deletedAt = optNullableString("deletedAt"),
        reminderTime = optNullableString("reminderTime"),
        recurrence = optString("recurrence", "NONE"),
        isHighPriority = optBoolean("isHighPriority"),
        isFullScreenReminder = optBoolean("isFullScreenReminder"),
        attachments = optJSONArray("attachments").toStringList(),
        isCompleted = optBoolean("isCompleted"),
        isArchived = optBoolean("isArchived"),
        isOneOffTask = optBoolean("isOneOffTask")
    )
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (i in 0 until length()) {
            add(optString(i))
        }
    }
}

private fun parseInstantMillis(value: String): Long? {
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
}
