package com.maxximum.kairos.domain.logic

import android.content.Context
import com.maxximum.kairos.domain.model.RecurrenceType
import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.domain.model.recurrenceType
import com.maxximum.kairos.notifications.AlarmScheduler

data class TodoCompletionResult(
    val updatedTodo: Todo,
    val deleted: Boolean,
    val message: String
)

fun Todo.applyCompletionChange(markCompleted: Boolean, now: Long = System.currentTimeMillis()): Todo {
    if (!markCompleted) {
        return copy(isCompleted = false)
    }

    return when (recurrenceType()) {
        RecurrenceType.NONE -> copy(isCompleted = true)
        RecurrenceType.DAILY -> {
            val baseTime = reminderTime ?: now
            var nextTime = baseTime
            while (nextTime <= now) {
                nextTime += 24L * 60L * 60L * 1000L
            }
            copy(
                isCompleted = false,
                reminderTime = nextTime,
                timestamp = now
            )
        }
        RecurrenceType.WEEKLY -> {
            val baseTime = reminderTime ?: now
            var nextTime = baseTime
            while (nextTime <= now) {
                nextTime += 7L * 24L * 60L * 60L * 1000L
            }
            copy(
                isCompleted = false,
                reminderTime = nextTime,
                timestamp = now
            )
        }
    }
}

suspend fun applyTodoCompletion(
    context: Context,
    todo: Todo,
    markCompleted: Boolean,
    updateTodo: suspend (Todo) -> Unit,
    deleteTodo: suspend (Todo) -> Unit
): TodoCompletionResult {
    val updated = todo.applyCompletionChange(markCompleted)
    val shouldDelete = markCompleted && todo.isOneOffTask && todo.recurrenceType() == RecurrenceType.NONE

    if (shouldDelete) {
        AlarmScheduler.cancel(context, updated)
        deleteTodo(updated)
        return TodoCompletionResult(updatedTodo = updated, deleted = true, message = "Task auto-deleted")
    }

    updateTodo(updated)
    if (updated.reminderTime != null && !updated.isCompleted) {
        AlarmScheduler.schedule(context, updated)
    } else {
        AlarmScheduler.cancel(context, updated)
    }

    val completedRecurring = markCompleted && todo.recurrenceType() != RecurrenceType.NONE
    return TodoCompletionResult(
        updatedTodo = updated,
        deleted = false,
        message = if (completedRecurring) {
            "Moved to next reminder"
        } else if (markCompleted) {
            "Task completed"
        } else {
            "Task marked pending"
        }
    )
}
