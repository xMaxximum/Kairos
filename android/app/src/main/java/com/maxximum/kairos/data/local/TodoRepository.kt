package com.maxximum.kairos.data.local

import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    val allTodos: Flow<List<Todo>>
    fun observeTodoById(id: Int): Flow<Todo?>
    suspend fun getAllTodosSnapshot(): List<Todo>
    suspend fun getPendingSyncTodos(): List<Todo>
    suspend fun getPendingSyncCount(): Int
    suspend fun getTodoById(id: Int): Todo?
    suspend fun getTodoByClientId(clientId: String): Todo?
    suspend fun insertTodo(todo: Todo): Long
    suspend fun insertSyncedTodo(todo: Todo): Long
    suspend fun updateTodo(todo: Todo)
    suspend fun updateSyncedTodo(todo: Todo)
    suspend fun deleteTodo(todo: Todo)
}

class LocalTodoRepository(private val todoDao: TodoDao) : TodoRepository {
    override val allTodos: Flow<List<Todo>> = todoDao.getAllTodos()
    override fun observeTodoById(id: Int): Flow<Todo?> = todoDao.observeTodoById(id)

    override suspend fun getAllTodosSnapshot(): List<Todo> = todoDao.getAllTodosSnapshot()

    override suspend fun getPendingSyncTodos(): List<Todo> = todoDao.getPendingSyncTodos()

    override suspend fun getPendingSyncCount(): Int = todoDao.getPendingSyncCount()

    override suspend fun getTodoById(id: Int): Todo? = todoDao.getTodoById(id)

    override suspend fun getTodoByClientId(clientId: String): Todo? = todoDao.getTodoByClientId(clientId)

    override suspend fun insertTodo(todo: Todo): Long = todoDao.insertTodo(todo.asDirty())

    override suspend fun insertSyncedTodo(todo: Todo): Long = todoDao.insertTodo(todo.asSynced())

    override suspend fun updateTodo(todo: Todo) = todoDao.updateTodo(todo.asDirty())

    override suspend fun updateSyncedTodo(todo: Todo) = todoDao.updateTodo(todo.asSynced())

    override suspend fun deleteTodo(todo: Todo) = todoDao.updateTodo(todo.asDeleted())
}

private fun Todo.asDirty(): Todo {
    val now = System.currentTimeMillis()
    return copy(
        updatedAt = now,
        syncStatus = SyncStatus.DIRTY.name
    )
}

private fun Todo.asDeleted(): Todo {
    val now = System.currentTimeMillis()
    return copy(
        deletedAt = now,
        updatedAt = now,
        syncStatus = SyncStatus.DIRTY.name
    )
}

private fun Todo.asSynced(): Todo {
    val now = System.currentTimeMillis()
    return copy(
        lastSyncedAt = now,
        syncStatus = SyncStatus.SYNCED.name
    )
}

