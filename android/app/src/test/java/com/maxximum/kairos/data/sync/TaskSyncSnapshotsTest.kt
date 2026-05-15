package com.maxximum.kairos.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskSyncSnapshotsTest {
    @Test
    fun changedFieldsDetectsNonOverlappingEdits() {
        val base = snapshot(title = "Base", description = "Base description")
        val local = base.copy(title = "Local title")
        val server = base.copy(description = "Server description")

        val localChanged = TaskSyncSnapshots.changedFields(base, local)
        val serverChanged = TaskSyncSnapshots.changedFields(base, server)

        assertTrue(localChanged.intersect(serverChanged).isEmpty())
    }

    @Test
    fun changedFieldsDetectsSameFieldConflict() {
        val base = snapshot(title = "Base")
        val local = base.copy(title = "Local title")
        val server = base.copy(title = "Server title")

        val localChanged = TaskSyncSnapshots.changedFields(base, local)
        val serverChanged = TaskSyncSnapshots.changedFields(base, server)

        assertEquals(setOf("title"), localChanged.intersect(serverChanged))
    }

    @Test
    fun mergeKeepsServerFieldsAndAppliesLocalChangedFields() {
        val base = snapshot(title = "Base", description = "Base description")
        val local = base.copy(title = "Local title")
        val server = base.copy(description = "Server description")
        val localChanged = TaskSyncSnapshots.changedFields(base, local)

        val merged = TaskSyncSnapshots.merge(server, local, localChanged)

        assertEquals("Local title", merged.title)
        assertEquals("Server description", merged.description)
    }

    private fun snapshot(
        title: String = "Task",
        description: String = "",
        updatedAt: Long = 1_000
    ): TaskSyncSnapshot {
        return TaskSyncSnapshot(
            clientId = "client-id",
            serverId = "server-id",
            remoteUpdatedAt = updatedAt,
            title = title,
            description = description,
            createdAt = 500,
            updatedAt = updatedAt,
            deletedAt = null,
            reminderTime = null,
            recurrence = "NONE",
            isHighPriority = false,
            isFullScreenReminder = false,
            attachments = emptyList(),
            isCompleted = false,
            isArchived = false,
            isOneOffTask = false
        )
    }
}
