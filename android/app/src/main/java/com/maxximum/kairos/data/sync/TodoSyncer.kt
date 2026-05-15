package com.maxximum.kairos.data.sync

import android.content.Context
import android.os.Build
import com.maxximum.kairos.data.auth.ApiServerStore
import com.maxximum.kairos.data.auth.AuthSessionStore
import com.maxximum.kairos.data.local.AppDatabase
import com.maxximum.kairos.data.local.LocalTodoRepository
import com.maxximum.kairos.data.local.SyncConflict
import com.maxximum.kairos.data.local.SyncConflictDao
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
    val skipped: Boolean = false,
    val conflicts: Int = 0,
    val autoMerged: Int = 0
) {
    fun statusMessage(): String {
        if (skipped) return "Sign in before syncing."
        if (conflicts > 0) return "$conflicts task${if (conflicts == 1) "" else "s"} need conflict review in Settings."
        if (autoMerged > 0) return "Auto-merged $autoMerged task${if (autoMerged == 1) "" else "s"}. Pending $pendingAfterSync."
        return "Uploaded $uploaded, deleted $deleted. Server returned $remoteReturned; imported $imported, updated $updated. Pending $pendingAfterSync."
    }
}

class TodoSyncer(
    private val context: Context,
    private val repository: TodoRepository = LocalTodoRepository(AppDatabase.getDatabase(context).todoDao()),
    private val conflictDao: SyncConflictDao = AppDatabase.getDatabase(context).syncConflictDao(),
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

        hydrateMissingRemoteMetadata(accessToken)

        val pendingTodos = repository.getPendingSyncTodos()
        var uploaded = 0
        var deleted = 0
        var conflicts = 0
        var autoMerged = 0

        pendingTodos.forEach { todo ->
            if (todo.deletedAt != null) {
                val remote = try {
                    if (todo.serverId != null) {
                        taskApi.deleteTaskByClientId(accessToken, todo)
                    } else {
                        null
                    }
                } catch (error: TaskApiException) {
                    if (error.isRecoverableTaskConflict()) {
                        when (recoverTaskConflict(accessToken, todo, error)) {
                            ConflictRecovery.AutoMergedUploaded -> {
                                deleted += 1
                                autoMerged += 1
                            }
                            ConflictRecovery.ManualConflict -> conflicts += 1
                        }
                        return@forEach
                    }
                    throw error
                }
                if (todo.isStillCurrentPending()) {
                    repository.updateSyncedTodo(remote?.toSyncedLocalTodo(todo) ?: todo)
                    deleted += 1
                }
            } else {
                val remote = try {
                    uploadTask(accessToken, todo)
                } catch (error: TaskApiException) {
                    if (error.isRecoverableTaskConflict()) {
                        when (recoverTaskConflict(accessToken, todo, error)) {
                            ConflictRecovery.AutoMergedUploaded -> {
                                uploaded += 1
                                autoMerged += 1
                            }
                            ConflictRecovery.ManualConflict -> conflicts += 1
                        }
                        return@forEach
                    }
                    throw error
                }
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
                    val todo = remote.toTodo().copy(
                        baseSnapshotJson = TaskSyncSnapshots.encode(remote.toTaskSyncSnapshot())
                    )
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
            pendingAfterSync = repository.getPendingSyncCount(),
            conflicts = conflicts,
            autoMerged = autoMerged
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

    private suspend fun hydrateMissingRemoteMetadata(accessToken: String) {
        val pendingWithMissingBase = repository.getPendingSyncTodos()
            .filter { it.serverId != null && it.remoteUpdatedAt == null }
        if (pendingWithMissingBase.isEmpty()) return

        val remoteByClientId = taskApi.getTasks(accessToken).associateBy { it.clientId }
        pendingWithMissingBase.forEach { local ->
            val remote = remoteByClientId[local.clientId] ?: return@forEach
            val current = repository.getTodoByClientId(local.clientId) ?: return@forEach
            if (current.syncStatus != SyncStatus.SYNCED.name && current.remoteUpdatedAt == null) {
                repository.updateTodo(current.withRemoteMetadata(remote))
            }
        }
    }

    private suspend fun recoverTaskConflict(accessToken: String, local: Todo, error: TaskApiException): ConflictRecovery {
        val serverTask = error.serverTask ?: taskApi.getTasks(accessToken).firstOrNull { it.clientId == local.clientId }
        if (serverTask != null) {
            val current = repository.getTodoByClientId(local.clientId) ?: return ConflictRecovery.ManualConflict
            if (current.syncStatus != SyncStatus.SYNCED.name) {
                val serverSnapshot = serverTask.toTaskSyncSnapshot()
                val serverSnapshotJson = TaskSyncSnapshots.encode(serverSnapshot)
                val localSnapshot = current.toTaskSyncSnapshot()
                val baseSnapshot = TaskSyncSnapshots.decode(current.baseSnapshotJson)

                if (baseSnapshot != null && current.deletedAt == null && serverSnapshot.deletedAt == null) {
                    val localChangedFields = TaskSyncSnapshots.changedFields(baseSnapshot, localSnapshot)
                    val serverChangedFields = TaskSyncSnapshots.changedFields(baseSnapshot, serverSnapshot)
                    val overlappingFields = localChangedFields.intersect(serverChangedFields)
                    if (overlappingFields.isEmpty()) {
                        val mergedSnapshot = TaskSyncSnapshots.merge(serverSnapshot, localSnapshot, localChangedFields)
                        val mergedTodo = mergedSnapshot.toTodo(current).copy(
                            serverId = serverTask.id,
                            remoteUpdatedAt = serverSnapshot.remoteUpdatedAt,
                            baseSnapshotJson = serverSnapshotJson,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                        repository.updateDirtyTodo(mergedTodo)
                        return uploadRecoveredMerge(accessToken, mergedTodo)
                    }

                    storeManualConflict(current, localSnapshot, serverSnapshot, overlappingFields.ifEmpty { localChangedFields + serverChangedFields })
                } else {
                    val fields = if (baseSnapshot == null) {
                        TaskSyncSnapshots.mergeableFields.toSet()
                    } else {
                        TaskSyncSnapshots.changedFields(baseSnapshot, localSnapshot) +
                            TaskSyncSnapshots.changedFields(baseSnapshot, serverSnapshot)
                    }
                    storeManualConflict(current, localSnapshot, serverSnapshot, fields)
                }
            }
        }
        return ConflictRecovery.ManualConflict
    }

    private suspend fun uploadRecoveredMerge(accessToken: String, mergedTodo: Todo): ConflictRecovery {
        return try {
            val remote = if (mergedTodo.deletedAt == null) {
                uploadTask(accessToken, mergedTodo)
            } else {
                taskApi.deleteTaskByClientId(accessToken, mergedTodo)
            }
            val current = repository.getTodoByClientId(mergedTodo.clientId) ?: return ConflictRecovery.ManualConflict
            if (current.syncStatus == SyncStatus.DIRTY.name) {
                repository.updateSyncedTodo(remote?.toSyncedLocalTodo(current) ?: current)
            }
            ConflictRecovery.AutoMergedUploaded
        } catch (error: TaskApiException) {
            ConflictRecovery.ManualConflict
        }
    }

    private suspend fun storeManualConflict(
        current: Todo,
        localSnapshot: TaskSyncSnapshot,
        serverSnapshot: TaskSyncSnapshot,
        conflictedFields: Set<String>
    ) {
        val serverSnapshotJson = TaskSyncSnapshots.encode(serverSnapshot)
        conflictDao.upsert(
            SyncConflict(
                objectType = TASK_OBJECT_TYPE,
                clientId = current.clientId,
                localSnapshotJson = TaskSyncSnapshots.encode(localSnapshot),
                serverSnapshotJson = serverSnapshotJson,
                conflictedFields = conflictedFields.sorted().joinToString(","),
                detectedAt = System.currentTimeMillis()
            )
        )
        repository.updateConflictedTodo(
            current.copy(
                serverId = serverSnapshot.serverId,
                remoteUpdatedAt = serverSnapshot.remoteUpdatedAt,
                baseSnapshotJson = serverSnapshotJson,
                lastSyncedAt = System.currentTimeMillis()
            )
        )
    }

    private fun TaskApiException.isRecoverableTaskConflict(): Boolean {
        return statusCode == 409 && code == "task_conflict"
    }

    private fun Todo.withRemoteMetadata(remote: RemoteTask): Todo {
        return copy(
            serverId = remote.id,
            remoteUpdatedAt = remote.updatedAtMillis,
            lastSyncedAt = System.currentTimeMillis(),
            baseSnapshotJson = TaskSyncSnapshots.encode(remote.toTaskSyncSnapshot()),
            syncStatus = SyncStatus.DIRTY.name
        )
    }

    private fun RemoteTask.toSyncedLocalTodo(local: Todo): Todo {
        return toTodo().copy(
            id = local.id,
            timestamp = local.timestamp,
            baseSnapshotJson = TaskSyncSnapshots.encode(toTaskSyncSnapshot())
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
        const val TASK_OBJECT_TYPE = "task"
    }

    private enum class ConflictRecovery {
        AutoMergedUploaded,
        ManualConflict
    }
}
