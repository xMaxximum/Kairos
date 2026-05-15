<script setup lang="ts">
import {
  Archive,
  Check,
  Circle,
  Clock3,
  LogOut,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Star,
  Trash2
} from 'lucide-vue-next'
import type { TaskItem } from '~/types/kairos'

const auth = useAuthStore()
const tasks = useTasksStore()

const authMode = ref<'login' | 'register'>('login')
const email = ref('')
const password = ref('')
const title = ref('')
const description = ref('')
const reminderLocal = ref('')
const recurrence = ref('NONE')
const isHighPriority = ref(false)
const isFullScreenReminder = ref(false)
const isOneOffTask = ref(false)
const selectedTaskId = ref<string | null>(null)
const settingsOpen = ref(false)
const toast = ref('')

const selectedTask = computed(() => {
  return tasks.visibleTasks.find((task) => task.id === selectedTaskId.value) || null
})

const lastSyncText = computed(() => {
  if (!tasks.lastSyncedAt) return 'Not synced yet'
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(tasks.lastSyncedAt))
})

onMounted(async () => {
  window.addEventListener('keydown', handleGlobalKeydown)
  auth.hydrate()
  tasks.hydrate()
  if (auth.isAuthenticated) {
    await safeRefresh()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
})

watch(
  () => auth.isAuthenticated,
  async (isAuthenticated) => {
    if (isAuthenticated) {
      await safeRefresh()
    }
  }
)

async function submitAuth() {
  try {
    if (authMode.value === 'register') {
      await auth.register(email.value, password.value)
    } else {
      await auth.login(email.value, password.value)
    }
    showToast(auth.message)
    await safeRefresh()
  } catch {
    showToast(auth.message || 'Could not sign in.')
  }
}

async function createTask() {
  try {
    await tasks.createTask({
      title: title.value,
      description: description.value,
      reminderTime: toIsoOrNull(reminderLocal.value),
      recurrence: recurrence.value,
      isHighPriority: isHighPriority.value,
      isFullScreenReminder: isFullScreenReminder.value,
      isOneOffTask: isOneOffTask.value
    })
    title.value = ''
    description.value = ''
    reminderLocal.value = ''
    recurrence.value = 'NONE'
    isHighPriority.value = false
    isFullScreenReminder.value = false
    isOneOffTask.value = false
    showToast('Task created.')
  } catch (error: any) {
    showToast(error?.message || 'Could not create task.')
  }
}

async function safeRefresh() {
  try {
    await tasks.fetchTasks()
    showToast('Tasks synced.')
  } catch {
    showToast(tasks.message || 'Using cached tasks.')
  }
}

async function patch(task: TaskItem, patch: Partial<TaskItem>) {
  try {
    await tasks.patchTask(task, patch)
  } catch (error: any) {
    showToast(error?.message || 'Could not update task.')
  }
}

async function toggleComplete(task: TaskItem) {
  try {
    await tasks.toggleComplete(task)
  } catch {
    showToast('Could not update task.')
  }
}

async function archiveTask(task: TaskItem) {
  try {
    await tasks.archiveTask(task)
  } catch {
    showToast('Could not update task.')
  }
}

async function deleteTask(task: TaskItem) {
  try {
    await tasks.deleteTask(task)
    if (selectedTaskId.value === task.id) selectedTaskId.value = null
    showToast('Task deleted.')
  } catch {
    showToast('Could not delete task.')
  }
}

function logout() {
  auth.logout()
  settingsOpen.value = false
  selectedTaskId.value = null
  showToast('Signed out.')
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    selectedTaskId.value = null
  }
}

function showToast(message: string) {
  toast.value = message
  window.setTimeout(() => {
    if (toast.value === message) toast.value = ''
  }, 2600)
}

function toLocalDateTimeValue(iso: string | null) {
  if (!iso) return ''
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return ''
  const offsetMs = date.getTimezoneOffset() * 60_000
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16)
}

function toIsoOrNull(value: string) {
  return value ? new Date(value).toISOString() : null
}

function formatReminder(iso: string | null) {
  if (!iso) return ''
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(iso))
}
</script>

<template>
  <main class="app-shell">
    <section class="sidebar">
      <div class="brand-row">
        <div>
          <p class="eyebrow">Kairos</p>
          <h1>Tasks</h1>
        </div>
        <div v-if="auth.isAuthenticated" class="settings-menu">
          <button class="icon-button" type="button" title="Settings" @click="settingsOpen = !settingsOpen">
            <Settings :size="18" />
          </button>
          <div v-if="settingsOpen" class="settings-popover">
            <p class="muted">{{ auth.user?.email }}</p>
            <button class="secondary-button" type="button" @click="logout">
              <LogOut :size="15" />
              Logout
            </button>
          </div>
        </div>
      </div>

      <form v-if="!auth.isAuthenticated" class="auth-panel" @submit.prevent="submitAuth">
        <div class="segmented">
          <button type="button" :class="{ active: authMode === 'login' }" @click="authMode = 'login'">Login</button>
          <button type="button" :class="{ active: authMode === 'register' }" @click="authMode = 'register'">Register</button>
        </div>
        <label>
          Email
          <input v-model="email" type="email" autocomplete="email" required>
        </label>
        <label>
          Password
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            minlength="8"
            required
          >
        </label>
        <button class="primary-button" type="submit" :disabled="auth.isLoading">
          {{ auth.isLoading ? 'Working...' : authMode === 'register' ? 'Create account' : 'Login' }}
        </button>
        <p class="muted">The web app uses the same account and API as Android.</p>
      </form>

      <template v-else>
        <form class="create-panel" @submit.prevent="createTask">
          <label>
            New task
            <input v-model="title" placeholder="What needs doing?" required>
          </label>
          <textarea v-model="description" rows="3" placeholder="Notes or details" />
          <div class="field-grid">
            <label>
              Reminder
              <input v-model="reminderLocal" type="datetime-local">
            </label>
            <label>
              Repeat
              <select v-model="recurrence">
                <option value="NONE">None</option>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
              </select>
            </label>
          </div>
          <div class="toggle-list">
            <label class="check-row">
              <input v-model="isHighPriority" type="checkbox">
              High priority
            </label>
            <label class="check-row">
              <input v-model="isFullScreenReminder" type="checkbox">
              Full-screen reminder
            </label>
            <label class="check-row">
              <input v-model="isOneOffTask" type="checkbox">
              Delete after completion
            </label>
          </div>
          <button class="primary-button" type="submit" :disabled="tasks.isSaving">
            <Plus :size="17" />
            {{ tasks.isSaving ? 'Saving...' : 'Add task' }}
          </button>
        </form>

        <div class="filter-grid">
          <button :class="{ active: tasks.filter === 'active' }" @click="tasks.filter = 'active'">
            Active <span>{{ tasks.counts.active }}</span>
          </button>
          <button :class="{ active: tasks.filter === 'pending' }" @click="tasks.filter = 'pending'">
            Pending <span>{{ tasks.counts.pending }}</span>
          </button>
          <button :class="{ active: tasks.filter === 'done' }" @click="tasks.filter = 'done'">
            Done <span>{{ tasks.counts.done }}</span>
          </button>
          <button :class="{ active: tasks.filter === 'archived' }" @click="tasks.filter = 'archived'">
            Archived <span>{{ tasks.counts.archived }}</span>
          </button>
        </div>
      </template>
    </section>

    <section class="task-surface">
      <div class="surface-header">
        <div>
          <h2>{{ auth.isAuthenticated ? 'Task list' : 'Offline cache' }}</h2>
          <p>{{ auth.isAuthenticated ? `Last sync: ${lastSyncText}` : 'Sign in to load your tasks.' }}</p>
        </div>
        <div v-if="auth.isAuthenticated" class="surface-tools">
          <label class="search-field">
            <Search :size="16" />
            <input v-model="tasks.searchQuery" type="search" placeholder="Search tasks">
          </label>
          <button class="secondary-button" type="button" title="Sync tasks" @click="safeRefresh">
            <RefreshCw :size="16" :class="{ spinning: tasks.isLoading }" />
            Sync
          </button>
        </div>
      </div>

      <div v-if="!auth.isAuthenticated && tasks.visibleTasks.length === 0" class="empty-state">
        <Clock3 :size="28" />
        <p>Login or register to use Kairos on the web.</p>
      </div>

      <div v-else class="task-layout">
        <div class="task-list" aria-label="Tasks">
          <button
            v-for="task in tasks.visibleTasks"
            :key="task.id"
            type="button"
            class="task-row"
            :class="{ selected: selectedTask?.id === task.id, done: task.isCompleted }"
            @click="selectedTaskId = task.id"
          >
            <span class="checkmark">
              <Check v-if="task.isCompleted" :size="15" />
              <Circle v-else :size="15" />
            </span>
            <span class="task-row-copy">
              <strong>{{ task.title }}</strong>
              <small>{{ task.description || 'No description' }}</small>
              <span class="meta-line">
                <span v-if="task.reminderTime">{{ formatReminder(task.reminderTime) }}</span>
                <span v-if="task.recurrence !== 'NONE'">{{ task.recurrence.toLowerCase() }}</span>
              </span>
            </span>
            <span class="row-tags">
              <span v-if="task.isHighPriority" class="tag priority">
                <Star :size="12" />
                High
              </span>
              <span v-if="task.isArchived" class="tag">Archived</span>
            </span>
          </button>
          <div v-if="auth.isAuthenticated && tasks.visibleTasks.length === 0" class="empty-state compact">
            <p>No tasks in this view.</p>
          </div>
        </div>

        <aside v-if="selectedTask" class="detail-panel">
          <label>
            Title
            <input :value="selectedTask.title" @change="patch(selectedTask, { title: ($event.target as HTMLInputElement).value })">
          </label>
          <label>
            Description
            <textarea
              :value="selectedTask.description"
              rows="8"
              @change="patch(selectedTask, { description: ($event.target as HTMLTextAreaElement).value })"
            />
          </label>
          <div class="field-grid">
            <label>
              Reminder
              <input
                type="datetime-local"
                :value="toLocalDateTimeValue(selectedTask.reminderTime)"
                @change="patch(selectedTask, { reminderTime: toIsoOrNull(($event.target as HTMLInputElement).value) })"
              >
            </label>
            <label>
              Repeat
              <select
                :value="selectedTask.recurrence"
                @change="patch(selectedTask, { recurrence: ($event.target as HTMLSelectElement).value })"
              >
                <option value="NONE">None</option>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
              </select>
            </label>
          </div>
          <div class="toggle-list">
            <label class="check-row">
              <input
                type="checkbox"
                :checked="selectedTask.isHighPriority"
                @change="patch(selectedTask, { isHighPriority: ($event.target as HTMLInputElement).checked })"
              >
              High priority
            </label>
            <label class="check-row">
              <input
                type="checkbox"
                :checked="selectedTask.isFullScreenReminder"
                @change="patch(selectedTask, { isFullScreenReminder: ($event.target as HTMLInputElement).checked })"
              >
              Full-screen reminder
            </label>
            <label class="check-row">
              <input
                type="checkbox"
                :checked="selectedTask.isOneOffTask"
                @change="patch(selectedTask, { isOneOffTask: ($event.target as HTMLInputElement).checked })"
              >
              Delete after completion
            </label>
          </div>
          <div class="detail-actions">
            <button class="secondary-button" type="button" @click="toggleComplete(selectedTask)">
              <Check :size="16" />
              {{ selectedTask.isCompleted ? 'Mark pending' : 'Complete' }}
            </button>
            <button class="secondary-button" type="button" @click="archiveTask(selectedTask)">
              <Archive :size="16" />
              {{ selectedTask.isArchived ? 'Unarchive' : 'Archive' }}
            </button>
            <button class="danger-button" type="button" @click="deleteTask(selectedTask)">
              <Trash2 :size="16" />
              Delete
            </button>
          </div>
        </aside>
      </div>
    </section>

    <Transition name="toast">
      <div v-if="toast" class="toast">{{ toast }}</div>
    </Transition>
  </main>
</template>
