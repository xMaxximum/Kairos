package com.maxximum.kairos

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import org.json.JSONArray

@Entity(tableName = "todos")
data class Todo(
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

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return try {
            val jsonArray = JSONArray(value)
            buildList(jsonArray.length()) {
                for (i in 0 until jsonArray.length()) {
                    add(jsonArray.optString(i))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return JSONArray(list).toString()
    }
}
