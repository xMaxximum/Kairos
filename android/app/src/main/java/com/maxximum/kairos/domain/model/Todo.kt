package com.maxximum.kairos.domain.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todos")
data class Todo(
    // Room keeps the current local primary key. Before adding sync, introduce a
    // persisted client-generated task ID so remote objects do not depend on it.
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reminderTime: Long? = null,
    val recurrence: String = RecurrenceType.NONE.name,
    val isHighPriority: Boolean = false,
    val isFullScreenReminder: Boolean = false,
    val attachments: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false,
    val isOneOffTask: Boolean = false
)

