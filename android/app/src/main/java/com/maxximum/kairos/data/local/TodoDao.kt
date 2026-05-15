package com.maxximum.kairos.data.local

import androidx.room.*
import com.maxximum.kairos.domain.model.Todo
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    fun getAllTodos(): Flow<List<Todo>>

    @Query("SELECT * FROM todos WHERE deletedAt IS NULL ORDER BY timestamp DESC")
    suspend fun getAllTodosSnapshot(): List<Todo>

    @Query("SELECT * FROM todos WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingSyncTodos(): List<Todo>

    @Query("SELECT COUNT(*) FROM todos WHERE syncStatus = 'DIRTY'")
    suspend fun getPendingSyncCount(): Int

    @Query("SELECT * FROM todos WHERE id = :id AND deletedAt IS NULL")
    suspend fun getTodoById(id: Int): Todo?

    @Query("SELECT * FROM todos WHERE clientId = :clientId")
    suspend fun getTodoByClientId(clientId: String): Todo?

    @Query("SELECT * FROM todos WHERE id = :id AND deletedAt IS NULL")
    fun observeTodoById(id: Int): Flow<Todo?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodo(todo: Todo): Long

    @Update
    suspend fun updateTodo(todo: Todo)

    @Delete
    suspend fun deleteTodo(todo: Todo)
}

