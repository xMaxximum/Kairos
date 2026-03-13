package com.maxximum.kairos

import kotlinx.coroutines.flow.Flow

class TodoRepository(private val todoDao: TodoDao) {
    val allTodos: Flow<List<Todo>> = todoDao.getAllTodos()

    suspend fun getTodoById(id: Int): Todo? = todoDao.getTodoById(id)

    suspend fun insertTodo(todo: Todo): Long = todoDao.insertTodo(todo)

    suspend fun updateTodo(todo: Todo) = todoDao.updateTodo(todo)

    suspend fun deleteTodo(todo: Todo) = todoDao.deleteTodo(todo)
}
