package com.maxximum.kairos

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

@Entity(tableName = "todos")
data class Todo(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val reminderTime: Long? = null,
    val isHighPriority: Boolean = false,
    val isFullScreenReminder: Boolean = false,
    val attachments: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val isArchived: Boolean = false
)

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType) ?: emptyList()
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return Gson().toJson(list)
    }
}
