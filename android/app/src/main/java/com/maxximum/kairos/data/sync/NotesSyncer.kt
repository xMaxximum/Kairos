package com.maxximum.kairos.data.sync

import android.content.Context
import android.os.Build
import com.maxximum.kairos.data.auth.ApiServerStore
import com.maxximum.kairos.data.auth.AuthSessionStore
import com.maxximum.kairos.data.local.AppDatabase
import com.maxximum.kairos.data.local.NoteDao
import com.maxximum.kairos.data.remote.AuthApi
import com.maxximum.kairos.data.remote.AuthApiException
import com.maxximum.kairos.data.remote.NotesApi
import com.maxximum.kairos.data.remote.TaskApiException
import com.maxximum.kairos.data.remote.toLocal
import com.maxximum.kairos.domain.model.SyncStatus

data class NotesSyncSummary(
    val uploaded: Int,
    val imported: Int,
    val skipped: Boolean = false
)

class NotesSyncer(
    private val context: Context,
    private val noteDao: NoteDao = AppDatabase.getDatabase(context).noteDao(),
    private val apiServerStore: ApiServerStore = ApiServerStore(context),
    private val sessionStore: AuthSessionStore = AuthSessionStore(context)
) {
    private val authApi = AuthApi { apiServerStore.getBaseUrl() }
    private val notesApi = NotesApi { apiServerStore.getBaseUrl() }

    suspend fun sync(): NotesSyncSummary {
        val accessToken = refreshedAccessToken() ?: return NotesSyncSummary(0, 0, skipped = true)
        var uploaded = 0

        noteDao.getPendingFolders().forEach { folder ->
            if (folder.deletedAt == null) {
                runCatching { notesApi.upsertFolder(accessToken, folder) }
                    .onSuccess { noteDao.updateFolder(it.toLocal(folder)); uploaded += 1 }
                    .onFailure { if (it !is TaskApiException || it.statusCode != 409) throw it }
            } else {
                noteDao.updateFolder(folder.copy(syncStatus = SyncStatus.SYNCED.name))
            }
        }
        noteDao.getPendingTags().forEach { tag ->
            if (tag.deletedAt == null) {
                runCatching { notesApi.upsertTag(accessToken, tag) }
                    .onSuccess { noteDao.updateTag(it.toLocal(tag)); uploaded += 1 }
                    .onFailure { if (it !is TaskApiException || it.statusCode != 409) throw it }
            } else {
                noteDao.updateTag(tag.copy(syncStatus = SyncStatus.SYNCED.name))
            }
        }
        noteDao.getPendingNotes().forEach { note ->
            if (note.deletedAt != null && note.serverId == null) {
                noteDao.updateNote(note.copy(syncStatus = SyncStatus.SYNCED.name))
                return@forEach
            }
            runCatching {
                if (note.deletedAt != null && note.serverId != null) notesApi.deleteNote(accessToken, note) else notesApi.upsertNote(accessToken, note)
            }.onSuccess {
                noteDao.updateNote(it.toLocal(note))
                uploaded += 1
            }.onFailure {
                if (it is TaskApiException && it.statusCode == 409) {
                    noteDao.updateNote(note.copy(syncStatus = SyncStatus.CONFLICT.name))
                } else {
                    throw it
                }
            }
        }
        noteDao.getPendingNoteTagLinks().forEach { link ->
            if (link.deletedAt == null) {
                runCatching { notesApi.upsertNoteTagLink(accessToken, link) }
                    .onSuccess { noteDao.updateNoteTagLink(it.toLocal(link)); uploaded += 1 }
                    .onFailure { if (it !is TaskApiException || it.statusCode != 409) throw it }
            } else {
                noteDao.updateNoteTagLink(link.copy(syncStatus = SyncStatus.SYNCED.name))
            }
        }
        noteDao.getPendingTaskTagLinks().forEach { link ->
            if (link.deletedAt == null) {
                runCatching { notesApi.upsertTaskTagLink(accessToken, link) }
                    .onSuccess { noteDao.updateTaskTagLink(it.toLocal(link)); uploaded += 1 }
                    .onFailure { if (it !is TaskApiException || it.statusCode != 409) throw it }
            } else {
                noteDao.updateTaskTagLink(link.copy(syncStatus = SyncStatus.SYNCED.name))
            }
        }

        var imported = 0
        val sync = notesApi.getSync(accessToken)
        sync.folders.forEach { remote ->
            val existing = noteDao.getFolderByClientId(remote.clientId)
            if (existing == null || existing.syncStatus == SyncStatus.SYNCED.name) {
                noteDao.insertFolder(remote.toLocal(existing))
                imported += 1
            }
        }
        sync.tags.forEach { remote ->
            val existing = noteDao.getTagByClientId(remote.clientId)
            if (existing == null || existing.syncStatus == SyncStatus.SYNCED.name) {
                noteDao.insertTag(remote.toLocal(existing))
                imported += 1
            }
        }
        sync.notes.forEach { remote ->
            val existing = noteDao.getNoteByClientId(remote.clientId)
            if (existing == null || existing.syncStatus == SyncStatus.SYNCED.name) {
                noteDao.insertNote(remote.toLocal(existing))
                imported += 1
            }
        }
        sync.noteTagLinks.forEach { remote ->
            val existing = noteDao.getNoteTagLinkByClientId(remote.clientId)
            if (existing == null || existing.syncStatus == SyncStatus.SYNCED.name) {
                noteDao.insertNoteTagLink(remote.toLocal(existing))
            }
        }
        sync.taskTagLinks.forEach { remote ->
            val existing = noteDao.getTaskTagLinkByClientId(remote.clientId)
            if (existing == null || existing.syncStatus == SyncStatus.SYNCED.name) {
                noteDao.insertTaskTagLink(remote.toLocal(existing))
            }
        }
        return NotesSyncSummary(uploaded, imported)
    }

    private suspend fun refreshedAccessToken(): String? {
        sessionStore.validAccessToken()?.let { return it }
        if (sessionStore.isRefreshTokenExpired()) return null

        val refreshToken = sessionStore.refreshToken()
        if (!refreshToken.isNullOrBlank()) {
            val tokens = try {
                authApi.refresh(refreshToken, deviceName())
            } catch (error: AuthApiException) {
                if (error.statusCode == 401) {
                    sessionStore.clear()
                    return null
                }
                throw error
            }
            sessionStore.save(tokens)
            return tokens.accessToken
        }
        return sessionStore.accessToken()
    }

    private fun deviceName(): String = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }
}
