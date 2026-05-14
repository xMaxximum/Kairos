import { defineStore } from 'pinia'
import type { CreateTaskRequest, TaskItem } from '~/types/kairos'

const CACHE_KEY = 'kairos.tasks.cache'

type TaskFilter = 'active' | 'pending' | 'done' | 'archived'

export const useTasksStore = defineStore('tasks', () => {
  const auth = useAuthStore()
  const tasks = ref<TaskItem[]>([])
  const isLoading = ref(false)
  const isSaving = ref(false)
  const filter = ref<TaskFilter>('active')
  const message = ref('')
  const lastSyncedAt = ref<number | null>(null)

  const visibleTasks = computed(() => {
    return tasks.value
      .filter((task) => !task.deletedAt)
      .filter((task) => {
        if (filter.value === 'active') return !task.isArchived
        if (filter.value === 'pending') return !task.isArchived && !task.isCompleted
        if (filter.value === 'done') return !task.isArchived && task.isCompleted
        return task.isArchived
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

  async function createTask(title: string, description = '') {
    const trimmed = title.trim()
    if (!trimmed) return
    isSaving.value = true
    try {
      const task = await auth.authFetch<TaskItem>('/tasks', {
        method: 'POST',
        body: {
          clientId: crypto.randomUUID(),
          title: trimmed,
          description,
          reminderTime: null,
          recurrence: 'NONE',
          isHighPriority: false,
          isFullScreenReminder: false,
          attachments: [],
          isCompleted: false,
          isArchived: false,
          isOneOffTask: false
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
      body: patch
    })
    upsertLocal(updated)
  }

  async function toggleComplete(task: TaskItem) {
    await patchTask(task, { isCompleted: !task.isCompleted })
  }

  async function archiveTask(task: TaskItem) {
    await patchTask(task, { isArchived: !task.isArchived })
  }

  async function deleteTask(task: TaskItem) {
    await auth.authFetch(`/tasks/${task.id}`, { method: 'DELETE' })
    tasks.value = tasks.value.filter((item) => item.id !== task.id)
    persist()
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
    deleteTask
  }
})

function readableError(error: any) {
  const data = error?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data?.error) return data.error
  if (error?.message) return error.message
  return 'Request failed.'
}
