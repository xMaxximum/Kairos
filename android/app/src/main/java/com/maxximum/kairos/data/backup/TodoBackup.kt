package com.maxximum.kairos.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.maxximum.kairos.domain.model.Todo

data class TodoBackupFile(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long = System.currentTimeMillis(),
    val todos: List<TodoBackupItem>
)

data class TodoBackupItem(
    val localId: Int,
    val clientId: String,
    val serverId: String?,
    val title: String,
    val description: String,
    val timestamp: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val lastSyncedAt: Long?,
    val syncStatus: String,
    val reminderTime: Long?,
    val recurrence: String,
    val isHighPriority: Boolean,
    val isFullScreenReminder: Boolean,
    val attachments: List<String>,
    val isCompleted: Boolean,
    val isArchived: Boolean,
    val isOneOffTask: Boolean
)

object TodoBackupWriter {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    fun write(context: Context, uri: Uri, todos: List<Todo>) {
        val backup = TodoBackupFile(todos = todos.map { it.toBackupItem() })
        context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
            output.writer(Charsets.UTF_8).use { writer ->
                gson.toJson(backup, writer)
            }
        } ?: error("Could not open backup destination.")
    }
}

private fun Todo.toBackupItem(): TodoBackupItem {
    return TodoBackupItem(
        localId = id,
        clientId = clientId,
        serverId = serverId,
        title = title,
        description = description,
        timestamp = timestamp,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
        lastSyncedAt = lastSyncedAt,
        syncStatus = syncStatus,
        reminderTime = reminderTime,
        recurrence = recurrence,
        isHighPriority = isHighPriority,
        isFullScreenReminder = isFullScreenReminder,
        attachments = attachments,
        isCompleted = isCompleted,
        isArchived = isArchived,
        isOneOffTask = isOneOffTask
    )
}
