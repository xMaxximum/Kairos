package com.maxximum.kairos.domain.model

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "todos",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["syncStatus"]),
        Index(value = ["deletedAt"])
    ]
)
data class Todo(
    // Room keeps the current local primary key for existing navigation/reminders.
    // clientId is the stable task identity used by backups and future sync.
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(defaultValue = "''")
    val clientId: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val remoteUpdatedAt: Long? = null,
    val title: String,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = timestamp,
    val deletedAt: Long? = null,
    val lastSyncedAt: Long? = null,
    val baseSnapshotJson: String? = null,
    @ColumnInfo(defaultValue = "'DIRTY'")
    val syncStatus: String = SyncStatus.DIRTY.name,
    val reminderTime: Long? = null,
    @ColumnInfo(defaultValue = "'NONE'")
    val recurrence: String = RecurrenceType.NONE.name,
    val isHighPriority: Boolean = false,
    val isFullScreenReminder: Boolean = false,
    val attachments: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "0")
    val isOneOffTask: Boolean = false
)

enum class SyncStatus {
    DIRTY,
    SYNCED,
    CONFLICT
}
