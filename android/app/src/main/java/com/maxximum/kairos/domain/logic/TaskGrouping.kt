package com.maxximum.kairos.domain.logic

import com.maxximum.kairos.domain.model.Todo
import java.util.Calendar

data class TodoSection(val label: String, val todos: List<Todo>)

fun groupTodosByDate(todos: List<Todo>): List<TodoSection> {
    val cal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val startOfToday = cal.timeInMillis
    val startOfTomorrow = startOfToday + 86_400_000L
    val startOfDayAfterTomorrow = startOfTomorrow + 86_400_000L
    val startOfNextWeek = startOfToday + 7 * 86_400_000L

    val pending = todos.filter { !it.isCompleted }
    val done = todos.filter { it.isCompleted }.sortedByDescending { it.timestamp }

    val overdue = pending.filter { it.reminderTime != null && it.reminderTime < startOfToday }
    val today = pending.filter { it.reminderTime != null && it.reminderTime in startOfToday until startOfTomorrow }
    val tomorrow = pending.filter { it.reminderTime != null && it.reminderTime in startOfTomorrow until startOfDayAfterTomorrow }
    val thisWeek = pending.filter { it.reminderTime != null && it.reminderTime in startOfDayAfterTomorrow until startOfNextWeek }
    val later = pending.filter { it.reminderTime != null && it.reminderTime >= startOfNextWeek }
    val noDate = pending.filter { it.reminderTime == null }

    return buildList {
        if (overdue.isNotEmpty()) add(TodoSection("Overdue", overdue))
        if (today.isNotEmpty()) add(TodoSection("Today", today))
        if (tomorrow.isNotEmpty()) add(TodoSection("Tomorrow", tomorrow))
        if (thisWeek.isNotEmpty()) add(TodoSection("This Week", thisWeek))
        if (later.isNotEmpty()) add(TodoSection("Later", later))
        if (noDate.isNotEmpty()) add(TodoSection("No Due Date", noDate))
        if (done.isNotEmpty()) add(TodoSection("Done", done))
    }
}
