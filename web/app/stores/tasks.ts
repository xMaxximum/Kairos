import { defineStore } from 'pinia'
import type { CreateTaskRequest, TaskItem } from '~/types/kairos'

const CACHE_KEY = 'kairos.tasks.cache'

type TaskFilter = 'active' | 'pending' | 'done' | 'archived'

interface CreateTaskInput {
  title: string
  description?: string
  reminderTime?: string | null
  recurrence?: string
  isHighPriority?: boolean
  isFullScreenReminder?: boolean
  isOneOffTask?: boolean
}

type TaskMutationResult =
  | { type: 'updated'; task: TaskItem }
  | { type: 'deleted'; task: TaskItem }

export const useTasksStore = defineStore('tasks', () => {
  const auth = useAuthStore()
  const tasks = ref<TaskItem[]>([])
  const isLoading = ref(false)
  const isSaving = ref(false)
  const filter = ref<TaskFilter>('active')
  const searchQuery = ref('')
  const message = ref('')
  const lastSyncedAt = ref<number | null>(null)

  const visibleTasks = computed(() => {
    const query = searchQuery.value.trim().toLowerCase()
    return tasks.value
      .filter((task) => !task.deletedAt)
      .filter((task) => {
        if (filter.value === 'active') return !task.isArchived
        if (filter.value === 'pending') return !task.isArchived && !task.isCompleted
        if (filter.value === 'done') return !task.isArchived && task.isCompleted
        return task.isArchived
      })
      .filter((task) => {
        if (!query) return true
        return [
          task.title,
          task.description,
          task.recurrence,
          task.reminderTime ? new Date(task.reminderTime).toLocaleString() : ''
        ].some((value) => value.toLowerCase().includes(query))
      })
      .sort((a, b) => {
        const aTime = a.reminderTime ? Date.parse(a.reminderTime) : Number.MAX_SAFE_INTEGER
        const bTime = b.reminderTime ? Date.parse(b.reminderTime) : Number.MAX_SAFE_INTEGER
        if (aTime !== bTime) return aTime - bTime
        return Date.parse(b.updatedAt) - Date.parse(a.updatedAt)
      })
  })

  const counts = computed(() => {
    const active = tasks.value.filter((task) => !task.deletedAt && !task.isArchived)
    return {
      active: active.length,
      pending: active.filter((task) => !task.isCompleted).length,
      done: active.filter((task) => task.isCompleted).length,
      archived: tasks.value.filter((task) => !task.deletedAt && task.isArchived).length
    }
  })

  function hydrate() {
    if (!import.meta.client) return
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return
    try {
      const parsed = JSON.parse(raw) as { tasks: TaskItem[]; lastSyncedAt: number | null }
      tasks.value = parsed.tasks || []
      lastSyncedAt.value = parsed.lastSyncedAt || null
    } catch {
      localStorage.removeItem(CACHE_KEY)
    }
  }

  function persist() {
    if (!import.meta.client) return
    localStorage.setItem(
      CACHE_KEY,
      JSON.stringify({
        tasks: tasks.value,
        lastSyncedAt: lastSyncedAt.value
      })
    )
  }

  async function fetchTasks() {
    if (!auth.isAuthenticated) return
    isLoading.value = true
    message.value = ''
    try {
      tasks.value = await auth.authFetch<TaskItem[]>('/tasks')
      lastSyncedAt.value = Date.now()
      persist()
      message.value = 'Tasks synced.'
    } catch (error) {
      message.value = readableError(error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function createTask(input: CreateTaskInput) {
    const trimmed = input.title.trim()
    if (!trimmed) return
    isSaving.value = true
    try {
      const task = await auth.authFetch<TaskItem>('/tasks', {
        method: 'POST',
        body: {
          clientId: crypto.randomUUID(),
          title: trimmed,
          description: input.description || '',
          reminderTime: input.reminderTime || null,
          recurrence: input.recurrence || 'NONE',
          isHighPriority: input.isHighPriority || false,
          isFullScreenReminder: input.isFullScreenReminder || false,
          attachments: [],
          isCompleted: false,
          isArchived: false,
          isOneOffTask: input.isOneOffTask || false
        } satisfies CreateTaskRequest
      })
      upsertLocal(task)
      message.value = 'Task created.'
    } finally {
      isSaving.value = false
    }
  }

  async function patchTask(task: TaskItem, patch: Partial<TaskItem>) {
    const updated = await auth.authFetch<TaskItem>(`/tasks/${task.id}`, {
      method: 'PATCH',
      body: {
        ...patch,
        baseUpdatedAt: task.updatedAt
      }
    })
    upsertLocal(updated)
    return updated
  }

  async function toggleComplete(task: TaskItem): Promise<TaskMutationResult> {
    if (!task.isCompleted && task.isOneOffTask && task.recurrence === 'NONE') {
      const completed = await patchTask(task, { isCompleted: true })
      const deleted = await deleteTask(completed)
      return { type: 'deleted', task: deleted }
    }
    const updated = await patchTask(task, { isCompleted: !task.isCompleted })
    return { type: 'updated', task: updated }
  }

  async function archiveTask(task: TaskItem) {
    return await patchTask(task, { isArchived: !task.isArchived })
  }

  async function deleteTask(task: TaskItem) {
    const deleted = await auth.authFetch<TaskItem>(`/tasks/${task.id}?baseUpdatedAt=${encodeURIComponent(task.updatedAt)}`, { method: 'DELETE' })
    upsertLocal(deleted)
    return deleted
  }

  async function restoreTask(task: TaskItem) {
    const restored = await auth.authFetch<TaskItem>(`/tasks/client/${task.clientId}/restore`, {
      method: 'POST',
      body: {
        title: task.title,
        description: task.description,
        reminderTime: task.reminderTime,
        recurrence: task.recurrence,
        isHighPriority: task.isHighPriority,
        isFullScreenReminder: task.isFullScreenReminder,
        attachments: task.attachments,
        isCompleted: task.isCompleted,
        isArchived: task.isArchived,
        isOneOffTask: task.isOneOffTask,
        baseUpdatedAt: task.updatedAt
      }
    })
    upsertLocal(restored)
    return restored
  }

  function upsertLocal(task: TaskItem) {
    const index = tasks.value.findIndex((item) => item.id === task.id || item.clientId === task.clientId)
    if (index >= 0) {
      tasks.value[index] = task
    } else {
      tasks.value.unshift(task)
    }
    lastSyncedAt.value = Date.now()
    persist()
  }

  return {
    tasks,
    visibleTasks,
    counts,
    filter,
    searchQuery,
    isLoading,
    isSaving,
    message,
    lastSyncedAt,
    hydrate,
    fetchTasks,
    createTask,
    patchTask,
    toggleComplete,
    archiveTask,
    deleteTask,
    restoreTask
  }
})

function readableError(error: any) {
  const data = error?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data?.error) return data.error
  if (error?.message) return error.message
  return 'Request failed.'
}
