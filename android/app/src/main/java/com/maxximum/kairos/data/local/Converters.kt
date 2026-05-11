package com.maxximum.kairos.data.local

import androidx.room.TypeConverter
import org.json.JSONArray

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
