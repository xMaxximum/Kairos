package com.maxximum.kairos.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxximum.kairos.data.local.AppDatabase
import com.maxximum.kairos.data.local.LocalTodoRepository
import com.maxximum.kairos.data.local.TodoRepository
import com.maxximum.kairos.domain.logic.TodoCompletionResult
import com.maxximum.kairos.domain.logic.applyTodoCompletion
import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.notifications.AlarmScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

sealed class UndoEvent {
    data class Delete(val message: String) : UndoEvent()
    data class Archive(val message: String) : UndoEvent()
}

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository
    val allTodos: Flow<List<Todo>>
    
    private var recentlyDeletedTodos: List<Todo> = emptyList()
    private var recentlyArchivedTodos: List<Todo> = emptyList()

    private val _undoEvents = MutableSharedFlow<UndoEvent>()
    val undoEvents: SharedFlow<UndoEvent> = _undoEvents

    init {
        val todoDao = AppDatabase.getDatabase(application).todoDao()
        repository = LocalTodoRepository(todoDao)
        allTodos = repository.allTodos
    }

    fun insert(todo: Todo, onResult: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repository.insertTodo(todo)
        onResult(id)
    }

    fun update(todo: Todo) = viewModelScope.launch {
        repository.updateTodo(todo)
    }

    fun toggleComplete(
        todo: Todo,
        markCompleted: Boolean,
        onResult: (TodoCompletionResult) -> Unit = {}
    ) = viewModelScope.launch {
        val result = applyTodoCompletion(
            context = getApplication(),
            todo = todo,
            markCompleted = markCompleted,
            updateTodo = { repository.updateTodo(it) },
            deleteTodo = {
                recentlyDeletedTodos = listOf(todo)
                repository.deleteTodo(it)
            }
        )
        if (result.deleted) {
            _undoEvents.emit(UndoEvent.Delete("Deleted ${todo.title}"))
        }
        onResult(result)
    }

    fun delete(todo: Todo, restoreTodo: Todo = todo) = viewModelScope.launch {
        recentlyDeletedTodos = listOf(restoreTodo)
        AlarmScheduler.cancel(getApplication(), todo)
        repository.deleteTodo(todo)
        _undoEvents.emit(UndoEvent.Delete("Deleted ${todo.title}"))
    }
    
    fun deleteMultiple(todos: List<Todo>) = viewModelScope.launch {
        recentlyDeletedTodos = todos
        todos.forEach { AlarmScheduler.cancel(getApplication(), it) }
        todos.forEach { repository.deleteTodo(it) }
        _undoEvents.emit(UndoEvent.Delete("Deleted ${todos.size} tasks"))
    }

    fun archiveMultiple(todos: List<Todo>) = viewModelScope.launch {
        recentlyArchivedTodos = todos
        todos.forEach { repository.updateTodo(it.copy(isArchived = true)) }
        _undoEvents.emit(UndoEvent.Archive("Archived ${todos.size} tasks"))
    }
    
    fun undoDelete() = viewModelScope.launch {
        recentlyDeletedTodos.forEach { todo ->
            repository.insertTodo(todo)
            if (todo.reminderTime != null && !todo.isCompleted && !todo.isArchived) {
                AlarmScheduler.schedule(getApplication(), todo)
            }
        }
        recentlyDeletedTodos = emptyList()
    }

    fun undoArchive() = viewModelScope.launch {
        recentlyArchivedTodos.forEach { repository.updateTodo(it.copy(isArchived = false)) }
        recentlyArchivedTodos = emptyList()
    }
    
    suspend fun getTodoById(id: Int): Todo? {
        return repository.getTodoById(id)
    }

    fun observeTodoById(id: Int): Flow<Todo?> {
        return repository.observeTodoById(id)
    }
}
