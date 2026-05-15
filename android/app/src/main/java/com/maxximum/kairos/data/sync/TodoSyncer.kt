package com.maxximum.kairos.data.sync

import android.content.Context
import android.os.Build
import com.maxximum.kairos.data.auth.ApiServerStore
import com.maxximum.kairos.data.auth.AuthSessionStore
import com.maxximum.kairos.data.local.AppDatabase
import com.maxximum.kairos.data.local.LocalTodoRepository
import com.maxximum.kairos.data.local.TodoRepository
import com.maxximum.kairos.data.remote.AuthApi
import com.maxximum.kairos.data.remote.AuthApiException
import com.maxximum.kairos.data.remote.RemoteTask
import com.maxximum.kairos.data.remote.TaskApi
import com.maxximum.kairos.data.remote.TaskApiException
import com.maxximum.kairos.domain.model.SyncStatus
import com.maxximum.kairos.domain.model.Todo
import com.maxximum.kairos.notifications.AlarmScheduler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TodoSyncSummary(
    val uploaded: Int,
    val deleted: Int,
    val remoteReturned: Int,
    val imported: Int,
    val updated: Int,
    val pendingAfterSync: Int,
    val skipped: Boolean = false
) {
    fun statusMessage(): String {
        if (skipped) return "Sign in before syncing."
        return "Uploaded $uploaded, deleted $deleted. Server returned $remoteReturned; imported $imported, updated $updated. Pending $pendingAfterSync."
    }
}

class TodoSyncer(
    private val context: Context,
    private val repository: TodoRepository = LocalTodoRepository(AppDatabase.getDatabase(context).todoDao()),
    private val apiServerStore: ApiServerStore = ApiServerStore(context),
    private val sessionStore: AuthSessionStore = AuthSessionStore(context)
) {
    private val authApi = AuthApi { apiServerStore.getBaseUrl() }
    private val taskApi = TaskApi { apiServerStore.getBaseUrl() }

    suspend fun sync(): TodoSyncSummary = syncMutex.withLock {
        syncLocked()
    }

    private suspend fun syncLocked(): TodoSyncSummary {
        val accessToken = refreshedAccessToken() ?: return TodoSyncSummary(
            uploaded = 0,
            deleted = 0,
            remoteReturned = 0,
            imported = 0,
            updated = 0,
            pendingAfterSync = repository.getPendingSyncCount(),
            skipped = true
        )

        val pendingTodos = repository.getPendingSyncTodos()
        var uploaded = 0
        var deleted = 0

        pendingTodos.forEach { todo ->
            if (todo.deletedAt != null) {
                var remote: RemoteTask? = null
                if (todo.serverId != null) {
                    remote = taskApi.deleteTaskByClientId(accessToken, todo)
                }
                if (todo.isStillCurrentPending()) {
                    repository.updateSyncedTodo(remote?.toSyncedLocalTodo(todo) ?: todo)
                    deleted += 1
                }
            } else {
                val remote = uploadTask(accessToken, todo)
                if (todo.isStillCurrentPending()) {
                    repository.updateSyncedTodo(remote.toSyncedLocalTodo(todo))
                    uploaded += 1
                }
            }
        }

        var imported = 0
        var updated = 0
        val remoteTasks = taskApi.getTasks(accessToken)
        remoteTasks.forEach { remote ->
            val local = repository.getTodoByClientId(remote.clientId)
            if (local == null) {
                if (remote.deletedAt == null) {
                    val todo = remote.toTodo()
                    val localId = repository.insertSyncedTodo(todo).toInt()
                    val savedTodo = todo.copy(id = localId)
                    scheduleIfNeeded(savedTodo)
                    imported += 1
                }
            } else if (local.syncStatus == SyncStatus.SYNCED.name && remote.isNewerThan(local)) {
                val updatedTodo = remote.toSyncedLocalTodo(local)
                repository.updateSyncedTodo(updatedTodo)
                if (updatedTodo.deletedAt != null || updatedTodo.isCompleted || updatedTodo.isArchived) {
                    AlarmScheduler.cancel(context, updatedTodo)
                } else {
                    scheduleIfNeeded(updatedTodo)
                }
                updated += 1
            }
        }

        return TodoSyncSummary(
            uploaded = uploaded,
            deleted = deleted,
            remoteReturned = remoteTasks.size,
            imported = imported,
            updated = updated,
            pendingAfterSync = repository.getPendingSyncCount()
        )
    }

    private suspend fun uploadTask(accessToken: String, todo: Todo): RemoteTask {
        return try {
            taskApi.upsertTask(accessToken, todo)
        } catch (error: TaskApiException) {
            if (error.statusCode == 409 && error.code == "task_deleted") {
                taskApi.restoreTaskByClientId(accessToken, todo)
            } else {
                throw error
            }
        }
    }

    private fun RemoteTask.toSyncedLocalTodo(local: Todo): Todo {
        return toTodo().copy(
            id = local.id,
            timestamp = local.timestamp
        )
    }

    private suspend fun Todo.isStillCurrentPending(): Boolean {
        val current = repository.getTodoByClientId(clientId) ?: return false
        return current.id == id &&
            current.updatedAt == updatedAt &&
            current.deletedAt == deletedAt &&
            current.syncStatus != SyncStatus.SYNCED.name
    }

    private fun RemoteTask.isNewerThan(local: Todo): Boolean {
        val remoteTodo = toTodo()
        return remoteTodo.updatedAt > local.updatedAt ||
            local.serverId != remoteTodo.serverId ||
            local.deletedAt != remoteTodo.deletedAt
    }

    private fun scheduleIfNeeded(todo: Todo) {
        if (todo.reminderTime != null && !todo.isCompleted && !todo.isArchived && todo.deletedAt == null) {
            AlarmScheduler.schedule(context, todo)
        }
    }

    private suspend fun refreshedAccessToken(): String? {
        sessionStore.validAccessToken()?.let { return it }
        if (sessionStore.isRefreshTokenExpired()) return null

        val refreshToken = sessionStore.refreshToken()
        if (!refreshToken.isNullOrBlank()) {
            val tokens = try {
                authApi.refresh(refreshToken, deviceName())
            } catch (error: AuthApiException) {
                if (error.statusCode == 401) {
                    sessionStore.clear()
                    return null
                }
                throw error
            }
            sessionStore.save(tokens)
            return tokens.accessToken
        }
        return sessionStore.accessToken()
    }

    private fun deviceName(): String {
        return "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android" }
    }

    private companion object {
        val syncMutex = Mutex()
    }
}
