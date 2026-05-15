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
import com.maxximum.kairos.data.local.NoteDao
import com.maxximum.kairos.data.local.SyncConflict
import com.maxximum.kairos.data.local.SyncConflictDao
import com.maxximum.kairos.data.local.TodoRepository
import com.maxximum.kairos.data.sync.TaskSyncSnapshots
import com.maxximum.kairos.data.sync.NotesSyncer
import com.maxximum.kairos.data.sync.TodoSyncer
import com.maxximum.kairos.data.sync.TodoSyncWorker
import com.maxximum.kairos.data.sync.toTodo
import com.maxximum.kairos.domain.logic.TodoCompletionResult
import com.maxximum.kairos.domain.logic.applyTodoCompletion
import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.domain.model.KairosTag
import com.maxximum.kairos.domain.model.LocalNoteNoteReference
import com.maxximum.kairos.domain.model.LocalNoteTagLink
import com.maxximum.kairos.domain.model.LocalNoteTaskReference
import com.maxximum.kairos.domain.model.LocalTaskTagLink
import com.maxximum.kairos.domain.model.Note
import com.maxximum.kairos.domain.model.NoteFolder
import com.maxximum.kairos.domain.model.SyncStatus
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
    private val noteDao: NoteDao
    private val conflictDao: SyncConflictDao
    private val apiServerStore = ApiServerStore(application)
    private val sessionStore = AuthSessionStore(application)
    private val syncer: TodoSyncer
    private val notesSyncer: NotesSyncer
    val allTodos: Flow<List<Todo>>
    val allNotes: Flow<List<Note>>
    val noteFolders: Flow<List<NoteFolder>>
    val allTags: Flow<List<KairosTag>>
    val noteTagLinks: Flow<List<LocalNoteTagLink>>
    val taskTagLinks: Flow<List<LocalTaskTagLink>>
    val noteTaskReferences: Flow<List<LocalNoteTaskReference>>
    val noteNoteReferences: Flow<List<LocalNoteNoteReference>>
    val syncConflicts: Flow<List<SyncConflict>>
    
    private var recentlyDeletedTodos: List<Todo> = emptyList()
    private var recentlyArchivedTodos: List<Todo> = emptyList()

    private val _undoEvents = MutableSharedFlow<UndoEvent>()
    val undoEvents: SharedFlow<UndoEvent> = _undoEvents
    private val _syncState = MutableStateFlow(SyncUiState())
    val syncState: StateFlow<SyncUiState> = _syncState.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        val todoDao = database.todoDao()
        noteDao = database.noteDao()
        conflictDao = database.syncConflictDao()
        repository = LocalTodoRepository(todoDao)
        syncer = TodoSyncer(
            context = application,
            repository = repository,
            conflictDao = conflictDao,
            apiServerStore = apiServerStore,
            sessionStore = sessionStore
        )
        notesSyncer = NotesSyncer(
            context = application,
            noteDao = noteDao,
            apiServerStore = apiServerStore,
            sessionStore = sessionStore
        )
        allTodos = repository.allTodos
        allNotes = noteDao.observeNotes()
        noteFolders = noteDao.observeFolders()
        allTags = noteDao.observeTags()
        noteTagLinks = noteDao.observeNoteTagLinks()
        taskTagLinks = noteDao.observeTaskTagLinks()
        noteTaskReferences = noteDao.observeNoteTaskReferences()
        noteNoteReferences = noteDao.observeNoteNoteReferences()
        syncConflicts = conflictDao.observeAll()
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
            val taskSummary = syncer.sync()
            val noteSummary = notesSyncer.sync()
            taskSummary to noteSummary
        }.onSuccess { (taskSummary, noteSummary) ->
            _syncState.value = SyncUiState(
                isSyncing = false,
                lastSyncedAtMillis = System.currentTimeMillis(),
                message = "${taskSummary.statusMessage()} Notes uploaded ${noteSummary.uploaded}, imported ${noteSummary.imported}."
            )
        }.onFailure { error ->
            _syncState.value = _syncState.value.copy(
                isSyncing = false,
                message = error.message ?: "Sync failed"
            )
        }
    }

    fun keepMineForConflict(conflict: SyncConflict) = viewModelScope.launch {
        val existing = repository.getTodoByClientId(conflict.clientId) ?: return@launch
        val localSnapshot = TaskSyncSnapshots.decode(conflict.localSnapshotJson) ?: return@launch
        val serverSnapshot = TaskSyncSnapshots.decode(conflict.serverSnapshotJson) ?: return@launch
        val serverSnapshotJson = TaskSyncSnapshots.encode(serverSnapshot)
        val resolved = localSnapshot.toTodo(existing).copy(
            serverId = serverSnapshot.serverId,
            remoteUpdatedAt = serverSnapshot.remoteUpdatedAt,
            baseSnapshotJson = serverSnapshotJson,
            lastSyncedAt = System.currentTimeMillis()
        )
        conflictDao.delete(conflict.objectType, conflict.clientId)
        repository.updateDirtyTodo(resolved)
        enqueueBackgroundSync()
    }

    fun useServerForConflict(conflict: SyncConflict) = viewModelScope.launch {
        val existing = repository.getTodoByClientId(conflict.clientId) ?: return@launch
        val serverSnapshot = TaskSyncSnapshots.decode(conflict.serverSnapshotJson) ?: return@launch
        val serverSnapshotJson = TaskSyncSnapshots.encode(serverSnapshot)
        val resolved = serverSnapshot.toTodo(existing).copy(baseSnapshotJson = serverSnapshotJson)
        conflictDao.delete(conflict.objectType, conflict.clientId)
        repository.updateSyncedTodo(resolved)
        if (resolved.deletedAt != null || resolved.isCompleted || resolved.isArchived) {
            AlarmScheduler.cancel(getApplication(), resolved)
        } else if (resolved.reminderTime != null) {
            AlarmScheduler.schedule(getApplication(), resolved)
        }
    }

    fun createNote(folderClientId: String? = null, onCreated: (Long) -> Unit = {}) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = noteDao.insertNote(
            Note(
                title = "Untitled note",
                folderClientId = folderClientId,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.DIRTY.name
            )
        )
        onCreated(id)
        enqueueBackgroundSync()
    }

    fun updateNote(note: Note) = viewModelScope.launch {
        val updated = note.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.DIRTY.name)
        noteDao.updateNote(updated)
        refreshLocalNoteReferences(updated)
        enqueueBackgroundSync()
    }

    fun deleteNote(note: Note) = viewModelScope.launch {
        val updated = note.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.DIRTY.name)
        noteDao.updateNote(updated)
        refreshLocalNoteReferences(updated)
        enqueueBackgroundSync()
    }

    fun createFolder(name: String, parentClientId: String? = null) = viewModelScope.launch {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return@launch
        val now = System.currentTimeMillis()
        noteDao.insertFolder(
            NoteFolder(
                name = trimmed,
                parentClientId = parentClientId,
                createdAt = now,
                updatedAt = now,
                syncStatus = SyncStatus.DIRTY.name
            )
        )
        enqueueBackgroundSync()
    }

    fun updateFolder(folder: NoteFolder) = viewModelScope.launch {
        noteDao.updateFolder(folder.copy(updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.DIRTY.name))
        enqueueBackgroundSync()
    }

    fun deleteFolder(folder: NoteFolder) = viewModelScope.launch {
        noteDao.updateFolder(folder.copy(deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(), syncStatus = SyncStatus.DIRTY.name))
        enqueueBackgroundSync()
    }

    fun addNoteTag(note: Note, tagName: String) = viewModelScope.launch {
        val tag = ensureLocalTag(tagName) ?: return@launch
        noteDao.insertNoteTagLink(LocalNoteTagLink(noteClientId = note.clientId, tagClientId = tag.clientId))
        enqueueBackgroundSync()
    }

    fun addTaskTag(todo: Todo, tagName: String) = viewModelScope.launch {
        val tag = ensureLocalTag(tagName) ?: return@launch
        noteDao.insertTaskTagLink(LocalTaskTagLink(taskClientId = todo.clientId, tagClientId = tag.clientId))
        enqueueBackgroundSync()
    }

    fun removeNoteTag(link: LocalNoteTagLink) = viewModelScope.launch {
        noteDao.updateNoteTagLink(link.copy(deletedAt = System.currentTimeMillis(), syncStatus = SyncStatus.DIRTY.name))
        enqueueBackgroundSync()
    }

    fun removeTaskTag(link: LocalTaskTagLink) = viewModelScope.launch {
        noteDao.updateTaskTagLink(link.copy(deletedAt = System.currentTimeMillis(), syncStatus = SyncStatus.DIRTY.name))
        enqueueBackgroundSync()
    }

    private suspend fun ensureLocalTag(name: String): KairosTag? {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return null
        val normalized = trimmed.uppercase()
        noteDao.getTagByNormalizedName(normalized)?.let { return it }
        val now = System.currentTimeMillis()
        val tag = KairosTag(
            name = trimmed,
            normalizedName = normalized,
            createdAt = now,
            updatedAt = now,
            syncStatus = SyncStatus.DIRTY.name
        )
        val id = noteDao.insertTag(tag)
        return tag.copy(id = id)
    }

    private suspend fun refreshLocalNoteReferences(note: Note) {
        noteDao.deleteTaskReferencesForNote(note.clientId)
        noteDao.deleteNoteReferencesForNote(note.clientId)
        if (note.deletedAt != null) return
        val regex = Regex("""\[\[(task|note):([0-9a-fA-F-]{36})(?:\|[^\]]+)?\]\]""")
        regex.findAll(note.markdownBody).forEach { match ->
            val type = match.groupValues[1]
            val id = match.groupValues[2]
            if (type == "task") {
                noteDao.insertTaskReference(LocalNoteTaskReference(noteClientId = note.clientId, taskClientId = id))
            } else if (id != note.clientId) {
                noteDao.insertNoteReference(LocalNoteNoteReference(sourceNoteClientId = note.clientId, targetNoteClientId = id))
            }
        }
    }

    private fun enqueueBackgroundSync() {
        TodoSyncWorker.enqueueNow(getApplication())
    }
}
