package com.maxximum.kairos.data.remote

import com.maxximum.kairos.domain.model.KairosTag
import com.maxximum.kairos.domain.model.LocalNoteTagLink
import com.maxximum.kairos.domain.model.LocalTaskTagLink
import com.maxximum.kairos.domain.model.Note
import com.maxximum.kairos.domain.model.NoteFolder
import com.maxximum.kairos.domain.model.SyncStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.OffsetDateTime

data class RemoteNotesSync(
    val notes: List<RemoteNote>,
    val folders: List<RemoteFolder>,
    val tags: List<RemoteTag>,
    val noteTagLinks: List<RemoteNoteTagLink>,
    val taskTagLinks: List<RemoteTaskTagLink>
)

data class RemoteNote(val id: String, val clientId: String, val folderClientId: String?, val title: String, val markdownBody: String, val createdAt: String, val updatedAt: String, val deletedAt: String?)
data class RemoteFolder(val id: String, val clientId: String, val parentClientId: String?, val name: String, val createdAt: String, val updatedAt: String, val deletedAt: String?)
data class RemoteTag(val id: String, val clientId: String, val name: String, val normalizedName: String, val createdAt: String, val updatedAt: String, val deletedAt: String?)
data class RemoteNoteTagLink(val id: String, val clientId: String, val noteClientId: String, val tagClientId: String, val updatedAt: String, val deletedAt: String?)
data class RemoteTaskTagLink(val id: String, val clientId: String, val taskClientId: String, val tagClientId: String, val updatedAt: String, val deletedAt: String?)

class NotesApi(private val baseUrlProvider: () -> String) {
    suspend fun getSync(accessToken: String): RemoteNotesSync = withContext(Dispatchers.IO) {
        val connection = openConnection("api/notes/sync").apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        val json = JSONObject(readTextResponse(connection))
        RemoteNotesSync(
            notes = json.getJSONArray("notes").mapObjects { it.toRemoteNote() },
            folders = json.getJSONArray("folders").mapObjects { it.toRemoteFolder() },
            tags = json.getJSONArray("tags").mapObjects { it.toRemoteTag() },
            noteTagLinks = json.getJSONArray("noteTagLinks").mapObjects { it.toRemoteNoteTagLink() },
            taskTagLinks = json.getJSONArray("taskTagLinks").mapObjects { it.toRemoteTaskTagLink() }
        )
    }

    suspend fun upsertNote(accessToken: String, note: Note): RemoteNote = withContext(Dispatchers.IO) {
        post("api/notes", accessToken, note.toJson()) { it.toRemoteNote() }
    }

    suspend fun deleteNote(accessToken: String, note: Note): RemoteNote = withContext(Dispatchers.IO) {
        delete("api/notes/${note.serverId}?baseUpdatedAt=${urlEncode(Instant.ofEpochMilli(note.remoteUpdatedAt ?: note.updatedAt).toString())}", accessToken) { it.toRemoteNote() }
    }

    suspend fun upsertFolder(accessToken: String, folder: NoteFolder): RemoteFolder = withContext(Dispatchers.IO) {
        post("api/note-folders", accessToken, folder.toJson()) { it.toRemoteFolder() }
    }

    suspend fun upsertTag(accessToken: String, tag: KairosTag): RemoteTag = withContext(Dispatchers.IO) {
        post("api/tags", accessToken, tag.toJson()) { it.toRemoteTag() }
    }

    suspend fun upsertNoteTagLink(accessToken: String, link: LocalNoteTagLink): RemoteNoteTagLink = withContext(Dispatchers.IO) {
        post("api/note-tag-links", accessToken, link.toJson()) { it.toRemoteNoteTagLink() }
    }

    suspend fun upsertTaskTagLink(accessToken: String, link: LocalTaskTagLink): RemoteTaskTagLink = withContext(Dispatchers.IO) {
        post("api/task-tag-links", accessToken, link.toJson()) { it.toRemoteTaskTagLink() }
    }

    private fun <T> post(path: String, accessToken: String, body: JSONObject, parse: (JSONObject) -> T): T {
        val connection = openConnection(path).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Authorization", "Bearer $accessToken")
            setRequestProperty("Content-Type", "application/json")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        return parse(JSONObject(readTextResponse(connection)))
    }

    private fun <T> delete(path: String, accessToken: String, parse: (JSONObject) -> T): T {
        val connection = openConnection(path).apply {
            requestMethod = "DELETE"
            setRequestProperty("Authorization", "Bearer $accessToken")
        }
        return parse(JSONObject(readTextResponse(connection)))
    }

    private fun openConnection(path: String): HttpURLConnection {
        val url = URL(baseUrlProvider().trimEnd('/') + "/" + path.trimStart('/'))
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun readTextResponse(connection: HttpURLConnection): String {
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        if (status !in 200..299) throw TaskApiException(readableError(text), status)
        return text
    }
}

fun RemoteNote.toLocal(existing: Note? = null): Note {
    val updated = parseMillis(updatedAt) ?: System.currentTimeMillis()
    return Note(
        id = existing?.id ?: 0,
        clientId = clientId,
        serverId = id,
        folderClientId = folderClientId,
        remoteUpdatedAt = updated,
        title = title,
        markdownBody = markdownBody,
        createdAt = parseMillis(createdAt) ?: updated,
        updatedAt = updated,
        deletedAt = deletedAt?.let(::parseMillis),
        lastSyncedAt = System.currentTimeMillis(),
        syncStatus = SyncStatus.SYNCED.name
    )
}

fun RemoteFolder.toLocal(existing: NoteFolder? = null): NoteFolder {
    val updated = parseMillis(updatedAt) ?: System.currentTimeMillis()
    return NoteFolder(
        id = existing?.id ?: 0,
        clientId = clientId,
        serverId = id,
        parentClientId = parentClientId,
        remoteUpdatedAt = updated,
        name = name,
        createdAt = parseMillis(createdAt) ?: updated,
        updatedAt = updated,
        deletedAt = deletedAt?.let(::parseMillis),
        lastSyncedAt = System.currentTimeMillis(),
        syncStatus = SyncStatus.SYNCED.name
    )
}

fun RemoteTag.toLocal(existing: KairosTag? = null): KairosTag {
    val updated = parseMillis(updatedAt) ?: System.currentTimeMillis()
    return KairosTag(
        id = existing?.id ?: 0,
        clientId = clientId,
        serverId = id,
        remoteUpdatedAt = updated,
        name = name,
        normalizedName = normalizedName,
        createdAt = parseMillis(createdAt) ?: updated,
        updatedAt = updated,
        deletedAt = deletedAt?.let(::parseMillis),
        lastSyncedAt = System.currentTimeMillis(),
        syncStatus = SyncStatus.SYNCED.name
    )
}

fun RemoteNoteTagLink.toLocal(existing: LocalNoteTagLink? = null): LocalNoteTagLink {
    return LocalNoteTagLink(
        id = existing?.id ?: 0,
        clientId = clientId,
        noteClientId = noteClientId,
        tagClientId = tagClientId,
        updatedAt = parseMillis(updatedAt) ?: System.currentTimeMillis(),
        deletedAt = deletedAt?.let(::parseMillis),
        syncStatus = SyncStatus.SYNCED.name
    )
}

fun RemoteTaskTagLink.toLocal(existing: LocalTaskTagLink? = null): LocalTaskTagLink {
    return LocalTaskTagLink(
        id = existing?.id ?: 0,
        clientId = clientId,
        taskClientId = taskClientId,
        tagClientId = tagClientId,
        updatedAt = parseMillis(updatedAt) ?: System.currentTimeMillis(),
        deletedAt = deletedAt?.let(::parseMillis),
        syncStatus = SyncStatus.SYNCED.name
    )
}

private fun Note.toJson(): JSONObject = JSONObject()
    .put("clientId", clientId)
    .put("folderClientId", folderClientId)
    .put("title", title)
    .put("markdownBody", markdownBody)
    .putBaseUpdatedAt(remoteUpdatedAt)

private fun NoteFolder.toJson(): JSONObject = JSONObject()
    .put("clientId", clientId)
    .put("parentClientId", parentClientId)
    .put("name", name)
    .putBaseUpdatedAt(remoteUpdatedAt)

private fun KairosTag.toJson(): JSONObject = JSONObject()
    .put("clientId", clientId)
    .put("name", name)
    .putBaseUpdatedAt(remoteUpdatedAt)

private fun LocalNoteTagLink.toJson(): JSONObject = JSONObject()
    .put("clientId", clientId)
    .put("noteClientId", noteClientId)
    .put("tagClientId", tagClientId)

private fun LocalTaskTagLink.toJson(): JSONObject = JSONObject()
    .put("clientId", clientId)
    .put("taskClientId", taskClientId)
    .put("tagClientId", tagClientId)

private fun JSONObject.putBaseUpdatedAt(value: Long?): JSONObject {
    return if (value == null) this else put("baseUpdatedAt", Instant.ofEpochMilli(value).toString())
}

private fun JSONObject.toRemoteNote(): RemoteNote = RemoteNote(getString("id"), getString("clientId"), optNullableString("folderClientId"), getString("title"), optString("markdownBody"), getString("createdAt"), getString("updatedAt"), optNullableString("deletedAt"))
private fun JSONObject.toRemoteFolder(): RemoteFolder = RemoteFolder(getString("id"), getString("clientId"), optNullableString("parentClientId"), getString("name"), getString("createdAt"), getString("updatedAt"), optNullableString("deletedAt"))
private fun JSONObject.toRemoteTag(): RemoteTag = RemoteTag(getString("id"), getString("clientId"), getString("name"), getString("normalizedName"), getString("createdAt"), getString("updatedAt"), optNullableString("deletedAt"))
private fun JSONObject.toRemoteNoteTagLink(): RemoteNoteTagLink = RemoteNoteTagLink(getString("id"), getString("clientId"), getString("noteClientId"), getString("tagClientId"), getString("updatedAt"), optNullableString("deletedAt"))
private fun JSONObject.toRemoteTaskTagLink(): RemoteTaskTagLink = RemoteTaskTagLink(getString("id"), getString("clientId"), getString("taskClientId"), getString("tagClientId"), getString("updatedAt"), optNullableString("deletedAt"))

private fun <T> JSONArray.mapObjects(mapper: (JSONObject) -> T): List<T> = buildList {
    for (i in 0 until length()) add(mapper(getJSONObject(i)))
}

private fun JSONObject.optNullableString(name: String): String? {
    if (!has(name) || isNull(name)) return null
    return optString(name).takeIf { it.isNotBlank() }
}

private fun readableError(text: String): String {
    if (text.isBlank()) return "Request failed"
    return runCatching { JSONObject(text).optString("error").takeIf { it.isNotBlank() } ?: text }.getOrDefault(text)
}

private fun urlEncode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

private fun parseMillis(value: String): Long? {
    return runCatching { Instant.parse(value).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
}
