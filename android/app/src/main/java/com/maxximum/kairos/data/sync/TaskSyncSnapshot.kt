package com.maxximum.kairos.data.sync

import com.google.gson.Gson
import com.maxximum.kairos.data.remote.RemoteTask
import com.maxximum.kairos.domain.model.Todo

data class TaskSyncSnapshot(
    val clientId: String,
    val serverId: String?,
    val remoteUpdatedAt: Long?,
    val title: String,
    val description: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long?,
    val reminderTime: Long?,
    val recurrence: String,
    val isHighPriority: Boolean,
    val isFullScreenReminder: Boolean,
    val attachments: List<String>,
    val isCompleted: Boolean,
    val isArchived: Boolean,
    val isOneOffTask: Boolean
)

object TaskSyncSnapshots {
    private val gson = Gson()

    val mergeableFields = listOf(
        "title",
        "description",
        "reminderTime",
        "recurrence",
        "isHighPriority",
        "isFullScreenReminder",
        "attachments",
        "isCompleted",
        "isArchived",
        "isOneOffTask",
        "deletedAt"
    )

    fun encode(snapshot: TaskSyncSnapshot): String = gson.toJson(snapshot)

    fun decode(json: String?): TaskSyncSnapshot? {
        if (json.isNullOrBlank()) return null
        return runCatching { gson.fromJson(json, TaskSyncSnapshot::class.java) }.getOrNull()
    }

    fun changedFields(base: TaskSyncSnapshot, changed: TaskSyncSnapshot): Set<String> {
        return mergeableFields.filterTo(mutableSetOf()) { field ->
            fieldValue(base, field) != fieldValue(changed, field)
        }
    }

    fun merge(server: TaskSyncSnapshot, local: TaskSyncSnapshot, localChangedFields: Set<String>): TaskSyncSnapshot {
        return localChangedFields.fold(server) { merged, field ->
            merged.copyFieldFrom(local, field)
        }
    }

    fun displayName(field: String): String {
        return when (field) {
            "title" -> "Title"
            "description" -> "Description"
            "reminderTime" -> "Reminder"
            "recurrence" -> "Repeat"
            "isHighPriority" -> "High priority"
            "isFullScreenReminder" -> "Full-screen reminder"
            "attachments" -> "Attachments"
            "isCompleted" -> "Completion"
            "isArchived" -> "Archive"
            "isOneOffTask" -> "Delete after completion"
            "deletedAt" -> "Deletion"
            else -> field
        }
    }

    fun displayValue(snapshot: TaskSyncSnapshot?, field: String): String {
        if (snapshot == null) return ""
        return when (field) {
            "title" -> snapshot.title
            "description" -> snapshot.description.ifBlank { "No description" }
            "reminderTime" -> snapshot.reminderTime?.toString() ?: "None"
            "recurrence" -> snapshot.recurrence
            "isHighPriority" -> yesNo(snapshot.isHighPriority)
            "isFullScreenReminder" -> yesNo(snapshot.isFullScreenReminder)
            "attachments" -> snapshot.attachments.joinToString().ifBlank { "None" }
            "isCompleted" -> yesNo(snapshot.isCompleted)
            "isArchived" -> yesNo(snapshot.isArchived)
            "isOneOffTask" -> yesNo(snapshot.isOneOffTask)
            "deletedAt" -> if (snapshot.deletedAt == null) "Not deleted" else "Deleted"
            else -> ""
        }
    }

    private fun yesNo(value: Boolean): String = if (value) "Yes" else "No"

    private fun fieldValue(snapshot: TaskSyncSnapshot, field: String): Any? {
        return when (field) {
            "title" -> snapshot.title
            "description" -> snapshot.description
            "reminderTime" -> snapshot.reminderTime
            "recurrence" -> snapshot.recurrence
            "isHighPriority" -> snapshot.isHighPriority
            "isFullScreenReminder" -> snapshot.isFullScreenReminder
            "attachments" -> snapshot.attachments
            "isCompleted" -> snapshot.isCompleted
            "isArchived" -> snapshot.isArchived
            "isOneOffTask" -> snapshot.isOneOffTask
            "deletedAt" -> snapshot.deletedAt
            else -> null
        }
    }

    private fun TaskSyncSnapshot.copyFieldFrom(source: TaskSyncSnapshot, field: String): TaskSyncSnapshot {
        return when (field) {
            "title" -> copy(title = source.title)
            "description" -> copy(description = source.description)
            "reminderTime" -> copy(reminderTime = source.reminderTime)
            "recurrence" -> copy(recurrence = source.recurrence)
            "isHighPriority" -> copy(isHighPriority = source.isHighPriority)
            "isFullScreenReminder" -> copy(isFullScreenReminder = source.isFullScreenReminder)
            "attachments" -> copy(attachments = source.attachments)
            "isCompleted" -> copy(isCompleted = source.isCompleted)
            "isArchived" -> copy(isArchived = source.isArchived)
            "isOneOffTask" -> copy(isOneOffTask = source.isOneOffTask)
            "deletedAt" -> copy(deletedAt = source.deletedAt)
            else -> this
        }
    }
}

fun Todo.toTaskSyncSnapshot(): TaskSyncSnapshot {
    return TaskSyncSnapshot(
        clientId = clientId,
        serverId = serverId,
        remoteUpdatedAt = remoteUpdatedAt,
        title = title,
        description = description,
        createdAt = timestamp,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
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

fun RemoteTask.toTaskSyncSnapshot(): TaskSyncSnapshot {
    val updatedAtMillis = updatedAtMillis ?: System.currentTimeMillis()
    return TaskSyncSnapshot(
        clientId = clientId,
        serverId = id,
        remoteUpdatedAt = updatedAtMillis,
        title = title,
        description = description,
        createdAt = parseRemoteMillis(createdAt) ?: updatedAtMillis,
        updatedAt = updatedAtMillis,
        deletedAt = deletedAt?.let(::parseRemoteMillis),
        reminderTime = reminderTime?.let(::parseRemoteMillis),
        recurrence = recurrence,
        isHighPriority = isHighPriority,
        isFullScreenReminder = isFullScreenReminder,
        attachments = attachments,
        isCompleted = isCompleted,
        isArchived = isArchived,
        isOneOffTask = isOneOffTask
    )
}

fun TaskSyncSnapshot.toTodo(existing: Todo): Todo {
    return existing.copy(
        clientId = clientId,
        serverId = serverId,
        remoteUpdatedAt = remoteUpdatedAt,
        title = title,
        description = description,
        timestamp = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
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

private fun parseRemoteMillis(value: String): Long? {
    return runCatching { java.time.Instant.parse(value).toEpochMilli() }
        .recoverCatching { java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .getOrNull()
}
