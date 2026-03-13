package com.maxximum.kairos

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
        repository = TodoRepository(todoDao)
        allTodos = repository.allTodos
    }

    fun insert(todo: Todo, onResult: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repository.insertTodo(todo)
        onResult(id)
    }

    fun update(todo: Todo) = viewModelScope.launch {
        repository.updateTodo(todo)
    }

    fun delete(todo: Todo) = viewModelScope.launch {
        recentlyDeletedTodos = listOf(todo)
        repository.deleteTodo(todo)
        _undoEvents.emit(UndoEvent.Delete("Deleted ${todo.title}"))
    }
    
    fun deleteMultiple(todos: List<Todo>) = viewModelScope.launch {
        recentlyDeletedTodos = todos
        todos.forEach { repository.deleteTodo(it) }
        _undoEvents.emit(UndoEvent.Delete("Deleted ${todos.size} tasks"))
    }

    fun archiveMultiple(todos: List<Todo>) = viewModelScope.launch {
        recentlyArchivedTodos = todos
        todos.forEach { repository.updateTodo(it.copy(isArchived = true)) }
        _undoEvents.emit(UndoEvent.Archive("Archived ${todos.size} tasks"))
    }
    
    fun undoDelete() = viewModelScope.launch {
        recentlyDeletedTodos.forEach { repository.insertTodo(it) }
        recentlyDeletedTodos = emptyList()
    }

    fun undoArchive() = viewModelScope.launch {
        recentlyArchivedTodos.forEach { repository.updateTodo(it.copy(isArchived = false)) }
        recentlyArchivedTodos = emptyList()
    }
    
    suspend fun getTodoById(id: Int): Todo? {
        return repository.getTodoById(id)
    }
}
