package com.maxximum.kairos.domain.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["folderClientId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["deletedAt"])
    ]
)
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val folderClientId: String? = null,
    val remoteUpdatedAt: Long? = null,
    val title: String,
    val markdownBody: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val lastSyncedAt: Long? = null,
    val baseSnapshotJson: String? = null,
    val syncStatus: String = SyncStatus.DIRTY.name
)

@Entity(
    tableName = "note_folders",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["parentClientId"]),
        Index(value = ["syncStatus"]),
        Index(value = ["deletedAt"])
    ]
)
data class NoteFolder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val parentClientId: String? = null,
    val remoteUpdatedAt: Long? = null,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val lastSyncedAt: Long? = null,
    val baseSnapshotJson: String? = null,
    val syncStatus: String = SyncStatus.DIRTY.name
)

@Entity(
    tableName = "tags",
    indices = [
        Index(value = ["clientId"], unique = true),
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["syncStatus"])
    ]
)
data class KairosTag(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val serverId: String? = null,
    val remoteUpdatedAt: Long? = null,
    val name: String,
    val normalizedName: String = name.trim().uppercase(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val deletedAt: Long? = null,
    val lastSyncedAt: Long? = null,
    val baseSnapshotJson: String? = null,
    val syncStatus: String = SyncStatus.DIRTY.name
)

@Entity(tableName = "note_tag_links", indices = [Index(value = ["clientId"], unique = true), Index(value = ["noteClientId", "tagClientId"], unique = true)])
data class LocalNoteTagLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val noteClientId: String,
    val tagClientId: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.DIRTY.name
)

@Entity(tableName = "task_tag_links", indices = [Index(value = ["clientId"], unique = true), Index(value = ["taskClientId", "tagClientId"], unique = true)])
data class LocalTaskTagLink(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: String = UUID.randomUUID().toString(),
    val taskClientId: String,
    val tagClientId: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
    val syncStatus: String = SyncStatus.DIRTY.name
)

@Entity(tableName = "note_task_references", indices = [Index(value = ["noteClientId", "taskClientId"], unique = true)])
data class LocalNoteTaskReference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteClientId: String,
    val taskClientId: String
)

@Entity(tableName = "note_note_references", indices = [Index(value = ["sourceNoteClientId", "targetNoteClientId"], unique = true)])
data class LocalNoteNoteReference(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceNoteClientId: String,
    val targetNoteClientId: String
)
