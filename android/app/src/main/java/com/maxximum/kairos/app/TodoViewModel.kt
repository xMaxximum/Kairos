package com.maxximum.kairos.app

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maxximum.kairos.data.backup.TodoBackupWriter
import com.maxximum.kairos.data.auth.ApiServerStore
import com.maxximum.kairos.data.auth.AuthSessionStore
import com.maxximum.kairos.data.local.AppDatabase
import com.maxximum.kairos.data.local.LocalTodoRepository
import com.maxximum.kairos.data.local.TodoRepository
import com.maxximum.kairos.data.sync.TodoSyncer
import com.maxximum.kairos.data.sync.TodoSyncWorker
import com.maxximum.kairos.domain.logic.TodoCompletionResult
import com.maxximum.kairos.domain.logic.applyTodoCompletion
import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.notifications.AlarmScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UndoEvent {
    data class Delete(val message: String) : UndoEvent()
    data class Archive(val message: String) : UndoEvent()
}

data class SyncUiState(
    val isSyncing: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val message: String? = null
)

class TodoViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: TodoRepository
    private val apiServerStore = ApiServerStore(application)
    private val sessionStore = AuthSessionStore(application)
    private val syncer: TodoSyncer
    val allTodos: Flow<List<Todo>>
    
    private var recentlyDeletedTodos: List<Todo> = emptyList()
    private var recentlyArchivedTodos: List<Todo> = emptyList()

    private val _undoEvents = MutableSharedFlow<UndoEvent>()
    val undoEvents: SharedFlow<UndoEvent> = _undoEvents
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    init {
        val todoDao = AppDatabase.getDatabase(application).todoDao()
        repository = LocalTodoRepository(todoDao)
        syncer = TodoSyncer(
            context = application,
            repository = repository,
            apiServerStore = apiServerStore,
            sessionStore = sessionStore
        )
        allTodos = repository.allTodos
    }

    fun insert(todo: Todo, onResult: (Long) -> Unit = {}) = viewModelScope.launch {
        val id = repository.insertTodo(todo)
        onResult(id)
        enqueueBackgroundSync()
    }

    fun update(todo: Todo) = viewModelScope.launch {
        repository.updateTodo(todo)
        enqueueBackgroundSync()
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
        enqueueBackgroundSync()
        onResult(result)
    }

    fun delete(todo: Todo, restoreTodo: Todo = todo) = viewModelScope.launch {
        recentlyDeletedTodos = listOf(restoreTodo)
        AlarmScheduler.cancel(getApplication(), todo)
        repository.deleteTodo(todo)
        enqueueBackgroundSync()
        _undoEvents.emit(UndoEvent.Delete("Deleted ${todo.title}"))
    }
    
    fun deleteMultiple(todos: List<Todo>) = viewModelScope.launch {
        recentlyDeletedTodos = todos
        todos.forEach { AlarmScheduler.cancel(getApplication(), it) }
        todos.forEach { repository.deleteTodo(it) }
        enqueueBackgroundSync()
        _undoEvents.emit(UndoEvent.Delete("Deleted ${todos.size} tasks"))
    }

    fun archiveMultiple(todos: List<Todo>) = viewModelScope.launch {
        recentlyArchivedTodos = todos
        todos.forEach { repository.updateTodo(it.copy(isArchived = true)) }
        enqueueBackgroundSync()
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
        enqueueBackgroundSync()
    }

    fun undoArchive() = viewModelScope.launch {
        recentlyArchivedTodos.forEach { repository.updateTodo(it.copy(isArchived = false)) }
        recentlyArchivedTodos = emptyList()
        enqueueBackgroundSync()
    }
    
    suspend fun getTodoById(id: Int): Todo? {
        return repository.getTodoById(id)
    }

    fun observeTodoById(id: Int): Flow<Todo?> {
        return repository.observeTodoById(id)
    }

    fun exportBackup(uri: Uri, onResult: (Boolean, String) -> Unit = { _, _ -> }) = viewModelScope.launch {
        runCatching {
            val todos = repository.getAllTodosSnapshot()
            TodoBackupWriter.write(getApplication(), uri, todos)
            todos.size
        }.onSuccess { count ->
            onResult(true, "Exported $count task${if (count == 1) "" else "s"}")
        }.onFailure { error ->
            onResult(false, error.message ?: "Could not export backup")
        }
    }

    fun syncNow() = viewModelScope.launch {
        _syncState.value = _syncState.value.copy(isSyncing = true, message = null)
        runCatching {
            syncer.sync()
        }.onSuccess { summary ->
            _syncState.value = SyncUiState(
                isSyncing = false,
                lastSyncedAtMillis = System.currentTimeMillis(),
                message = summary.statusMessage()
            )
        }.onFailure { error ->
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                message = error.message ?: "Sync failed"
            )
        }
    }

    private fun enqueueBackgroundSync() {
        TodoSyncWorker.enqueueNow(getApplication())
    }
}
