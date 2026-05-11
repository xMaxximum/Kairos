package com.maxximum.kairos.domain.model

enum class RecurrenceType(val label: String) {
    NONE("Does not repeat"),
    DAILY("Daily"),
    WEEKLY("Weekly")
}

fun Todo.recurrenceType(): RecurrenceType {
    return RecurrenceType.entries.firstOrNull { it.name == recurrence } ?: RecurrenceType.NONE
}

fun RecurrenceType.shortLabel(): String {
    return when (this) {
        RecurrenceType.NONE -> "Off"
        RecurrenceType.DAILY -> "Daily"
        RecurrenceType.WEEKLY -> "Weekly"
    }
}

