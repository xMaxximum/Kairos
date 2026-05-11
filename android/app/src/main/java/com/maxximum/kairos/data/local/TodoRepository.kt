package com.maxximum.kairos.data.local

import com.maxximum.kairos.domain.model.Todo
import kotlinx.coroutines.flow.Flow

interface TodoRepository {
    val allTodos: Flow<List<Todo>>
    fun observeTodoById(id: Int): Flow<Todo?>
    suspend fun getTodoById(id: Int): Todo?
    suspend fun insertTodo(todo: Todo): Long
    suspend fun updateTodo(todo: Todo)
    suspend fun deleteTodo(todo: Todo)
}

class LocalTodoRepository(private val todoDao: TodoDao) : TodoRepository {
    override val allTodos: Flow<List<Todo>> = todoDao.getAllTodos()
    override fun observeTodoById(id: Int): Flow<Todo?> = todoDao.observeTodoById(id)

    override suspend fun getTodoById(id: Int): Todo? = todoDao.getTodoById(id)

    override suspend fun insertTodo(todo: Todo): Long = todoDao.insertTodo(todo)

    override suspend fun updateTodo(todo: Todo) = todoDao.updateTodo(todo)

    override suspend fun deleteTodo(todo: Todo) = todoDao.deleteTodo(todo)
}

