<script setup lang="ts">
import {
  Archive,
  Check,
  Circle,
  Clock3,
  Eye,
  FileText,
  Folder,
  FolderPlus,
  Link2,
  LogOut,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Settings,
  Star,
  Tag,
  Trash2,
  X
} from 'lucide-vue-next'
import type { NoteFolder, NoteItem, NoteTagLink, TaskItem, TaskTagLink } from '~/types/kairos'

type ToastState = {
  message: string
  actionLabel?: string
  action?: () => Promise<void> | void
}

const auth = useAuthStore()
const tasks = useTasksStore()
const notes = useNotesStore()

const mode = ref<'tasks' | 'notes'>('tasks')
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
const pendingDeleteTask = ref<TaskItem | null>(null)
const newFolderName = ref('')
const renameFolderName = ref('')
const tagDraft = ref('')
const taskTagDraft = ref('')
const previewNote = ref(false)
const toast = ref<ToastState | null>(null)
let toastTimer: ReturnType<typeof window.setTimeout> | null = null

const selectedTask = computed(() => {
  return displayedTasks.value.find((task) => task.id === selectedTaskId.value) || null
})

const selectedFolder = computed(() => {
  return notes.activeFolders.find((folder) => folder.clientId === notes.selectedFolderClientId) || null
})

const displayedTasks = computed(() => {
  const folderId = notes.selectedFolderClientId
  if (!folderId) return tasks.visibleTasks
  const noteClientIds = notes.notesInFolderTree(folderId).map((note) => note.clientId)
  const linkedTaskIds = new Set(
    notes.noteTaskReferences
      .filter((reference) => noteClientIds.includes(reference.noteClientId))
      .map((reference) => reference.taskClientId)
  )
  return tasks.visibleTasks.filter((task) => linkedTaskIds.has(task.clientId))
})

const linkedTasksForSelectedNote = computed(() => {
  const note = notes.selectedNote
  if (!note) return []
  const linkedTaskIds = new Set(notes.taskLinksForNote(note.clientId).map((reference) => reference.taskClientId))
  return tasks.tasks.filter((task) => !task.deletedAt && linkedTaskIds.has(task.clientId))
})

const noteBacklinks = computed(() => {
  const note = notes.selectedNote
  if (!note) return []
  const sourceIds = notes.noteBacklinks(note.clientId).map((reference) => reference.sourceNoteClientId)
  return notes.activeNotes.filter((candidate) => sourceIds.includes(candidate.clientId))
})

const lastSyncText = computed(() => {
  const stamp = Math.max(tasks.lastSyncedAt || 0, notes.lastSyncedAt || 0)
  if (!stamp) return 'Not synced yet'
  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(stamp))
})

onMounted(async () => {
  window.addEventListener('keydown', handleGlobalKeydown)
  auth.hydrate()
  tasks.hydrate()
  notes.hydrate()
  if (auth.isAuthenticated) {
    await safeRefresh()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleGlobalKeydown)
  if (toastTimer) window.clearTimeout(toastTimer)
})

watch(
  () => auth.isAuthenticated,
  async (isAuthenticated) => {
    if (isAuthenticated) {
      await safeRefresh()
    }
  }
)

watch(selectedFolder, (folder) => {
  renameFolderName.value = folder?.name || ''
})

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
    await Promise.all([tasks.fetchTasks(), notes.fetchAll()])
    showToast('Synced.')
  } catch {
    showToast(tasks.message || notes.message || 'Using cached data.')
  }
}

async function createNote() {
  try {
    const note = await notes.createNote('Untitled note')
    mode.value = 'notes'
    showToast(`Created ${note.title}.`)
  } catch (error: any) {
    await handleSyncError(error, 'Could not create note.')
  }
}

async function patchNote(note: NoteItem, patch: Partial<Pick<NoteItem, 'title' | 'markdownBody' | 'folderClientId'>>) {
  try {
    await notes.patchNote(note, patch)
  } catch (error: any) {
    await handleSyncError(error, 'Could not update note.')
  }
}

async function deleteSelectedNote() {
  const note = notes.selectedNote
  if (!note) return
  try {
    await notes.deleteNote(note)
    showToast('Note deleted.')
  } catch (error: any) {
    await handleSyncError(error, 'Could not delete note.')
  }
}

async function createFolder() {
  const name = newFolderName.value.trim()
  if (!name) return
  try {
    const folder = await notes.createFolder(name)
    newFolderName.value = ''
    notes.selectedFolderClientId = folder.clientId
    showToast('Folder created.')
  } catch (error: any) {
    await handleSyncError(error, 'Could not create folder.')
  }
}

async function renameSelectedFolder() {
  const folder = selectedFolder.value
  const name = renameFolderName.value.trim()
  if (!folder || !name || name === folder.name) return
  try {
    await notes.patchFolder(folder, { name })
    showToast('Folder renamed.')
  } catch (error: any) {
    await handleSyncError(error, 'Could not rename folder.')
  }
}

async function deleteSelectedFolder() {
  const folder = selectedFolder.value
  if (!folder) return
  try {
    await notes.deleteFolder(folder)
    showToast('Folder deleted.')
  } catch (error: any) {
    await handleSyncError(error, 'Could not delete folder.')
  }
}

async function addTagToSelectedNote() {
  const note = notes.selectedNote
  const name = tagDraft.value.trim()
  if (!note || !name) return
  try {
    await notes.addNoteTag(note, name)
    tagDraft.value = ''
  } catch (error: any) {
    await handleSyncError(error, 'Could not add tag.')
  }
}

async function addTagToSelectedTask() {
  const task = selectedTask.value
  const name = taskTagDraft.value.trim()
  if (!task || !name) return
  try {
    await notes.addTaskTag(task.clientId, name)
    taskTagDraft.value = ''
  } catch (error: any) {
    await handleSyncError(error, 'Could not add task tag.')
  }
}

async function removeNoteTag(link: NoteTagLink) {
  try {
    await notes.removeNoteTag(link)
  } catch (error: any) {
    await handleSyncError(error, 'Could not remove tag.')
  }
}

async function removeTaskTag(link: TaskTagLink) {
  try {
    await notes.removeTaskTag(link)
  } catch (error: any) {
    await handleSyncError(error, 'Could not remove tag.')
  }
}

async function patch(task: TaskItem, patch: Partial<TaskItem>) {
  try {
    await tasks.patchTask(task, patch)
  } catch (error: any) {
    await handleSyncError(error, 'Could not update task.')
  }
}

async function toggleComplete(task: TaskItem) {
  try {
    const result = await tasks.toggleComplete(task)
    if (result.type === 'deleted') {
      if (selectedTaskId.value === task.id) selectedTaskId.value = null
      showToast('Task completed and deleted.', 'Undo', () => undoDeletedTask(result.task))
    }
  } catch (error: any) {
    await handleSyncError(error, 'Could not update task.')
  }
}

async function archiveTask(task: TaskItem) {
  try {
    const updated = await tasks.archiveTask(task)
    showToast(updated.isArchived ? 'Task archived.' : 'Task unarchived.', 'Undo', () => undoArchiveTask(updated, task.isArchived))
  } catch (error: any) {
    await handleSyncError(error, 'Could not update task.')
  }
}

function requestDeleteTask(task: TaskItem) {
  pendingDeleteTask.value = task
}

async function confirmDeleteTask() {
  const task = pendingDeleteTask.value
  if (!task) return
  pendingDeleteTask.value = null
  try {
    const deleted = await tasks.deleteTask(task)
    if (selectedTaskId.value === task.id) selectedTaskId.value = null
    showToast('Task deleted.', 'Undo', () => undoDeletedTask(deleted))
  } catch (error: any) {
    await handleSyncError(error, 'Could not delete task.')
  }
}

async function undoDeletedTask(task: TaskItem) {
  try {
    const restored = await tasks.restoreTask(task)
    selectedTaskId.value = restored.id
    showToast('Task restored.')
  } catch (error: any) {
    await handleSyncError(error, 'Could not restore task.')
  }
}

async function undoArchiveTask(task: TaskItem, previousArchived: boolean) {
  try {
    const restored = await tasks.patchTask(task, { isArchived: previousArchived })
    selectedTaskId.value = restored.id
    showToast(previousArchived ? 'Task archived.' : 'Task unarchived.')
  } catch (error: any) {
    await handleSyncError(error, 'Could not undo archive.')
  }
}

function noteTagLinks(note: NoteItem) {
  return notes.noteTagLinks.filter((link) => !link.deletedAt && link.noteClientId === note.clientId)
}

function taskTagLinks(task: TaskItem) {
  return notes.taskTagLinks.filter((link) => !link.deletedAt && link.taskClientId === task.clientId)
}

function tagName(tagClientId: string) {
  return notes.activeTags.find((tag) => tag.clientId === tagClientId)?.name || 'Tag'
}

function insertTaskReference(task: TaskItem) {
  const note = notes.selectedNote
  if (!note) return
  const token = `[[task:${task.clientId}|${task.title}]]`
  patchNote(note, { markdownBody: `${note.markdownBody}${note.markdownBody ? '\n' : ''}${token}` })
}

function insertNoteReference(target: NoteItem) {
  const note = notes.selectedNote
  if (!note || note.clientId === target.clientId) return
  const token = `[[note:${target.clientId}|${target.title}]]`
  patchNote(note, { markdownBody: `${note.markdownBody}${note.markdownBody ? '\n' : ''}${token}` })
}

function logout() {
  auth.logout()
  settingsOpen.value = false
  selectedTaskId.value = null
  notes.selectedNoteClientId = null
  showToast('Signed out.')
}

function handleGlobalKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    if (pendingDeleteTask.value) {
      pendingDeleteTask.value = null
      return
    }
    selectedTaskId.value = null
  }
}

async function handleSyncError(error: any, fallback: string) {
  if (typeof error?.data?.code === 'string' && error.data.code.includes('conflict')) {
    try {
      await safeRefresh()
    } catch {
      // Keep conflict copy even if refresh fails.
    }
    showToast('Changed elsewhere. Refreshed the latest version.')
    return
  }
  showToast(error?.data?.error || error?.message || fallback)
}

function showToast(message: string, actionLabel?: string, action?: ToastState['action']) {
  if (toastTimer) window.clearTimeout(toastTimer)
  toast.value = { message, actionLabel, action }
  toastTimer = window.setTimeout(() => {
    if (toast.value?.message === message) toast.value = null
  }, 2600)
}

async function runToastAction() {
  const action = toast.value?.action
  toast.value = null
  if (toastTimer) window.clearTimeout(toastTimer)
  toastTimer = null
  await action?.()
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

function renderMarkdown(markdown: string) {
  const linked = escapeHtml(markdown)
    .replace(/\[\[(task|note):([0-9a-fA-F-]{36})(?:\|([^\]]+))?\]\]/g, (_match, type, id, label) => {
      const text = label || id
      return `<span class="wiki-link" data-type="${type}" data-id="${id}">${type === 'task' ? 'Task' : 'Note'}: ${text}</span>`
    })
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')

  const lines = linked.split('\n')
  const html: string[] = []
  let inList = false
  lines.forEach((line) => {
    if (/^###\s+/.test(line)) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      html.push(`<h3>${line.replace(/^###\s+/, '')}</h3>`)
    } else if (/^##\s+/.test(line)) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      html.push(`<h2>${line.replace(/^##\s+/, '')}</h2>`)
    } else if (/^#\s+/.test(line)) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      html.push(`<h1>${line.replace(/^#\s+/, '')}</h1>`)
    } else if (/^[-*]\s+/.test(line)) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${line.replace(/^[-*]\s+/, '')}</li>`)
    } else if (line.trim()) {
      if (inList) {
        html.push('</ul>')
        inList = false
      }
      html.push(`<p>${line}</p>`)
    } else if (inList) {
      html.push('</ul>')
      inList = false
    }
  })
  if (inList) html.push('</ul>')
  return html.join('')
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}
</script>

<template>
  <main class="app-shell">
    <section class="sidebar">
      <div class="brand-row">
        <div>
          <p class="eyebrow">Kairos</p>
          <h1>{{ mode === 'tasks' ? 'Tasks' : 'Notes' }}</h1>
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
          <input v-model="password" type="password" autocomplete="current-password" minlength="8" required>
        </label>
        <button class="primary-button" type="submit" :disabled="auth.isLoading">
          {{ auth.isLoading ? 'Working...' : authMode === 'register' ? 'Create account' : 'Login' }}
        </button>
      </form>

      <template v-else>
        <div class="segmented">
          <button type="button" :class="{ active: mode === 'tasks' }" @click="mode = 'tasks'">
            <Check :size="15" />
            Tasks
          </button>
          <button type="button" :class="{ active: mode === 'notes' }" @click="mode = 'notes'">
            <FileText :size="15" />
            Notes
          </button>
        </div>

        <form v-if="mode === 'tasks'" class="create-panel" @submit.prevent="createTask">
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

        <div v-if="mode === 'tasks'" class="filter-grid">
          <button :class="{ active: tasks.filter === 'active' }" @click="tasks.filter = 'active'">Active <span>{{ tasks.counts.active }}</span></button>
          <button :class="{ active: tasks.filter === 'pending' }" @click="tasks.filter = 'pending'">Pending <span>{{ tasks.counts.pending }}</span></button>
          <button :class="{ active: tasks.filter === 'done' }" @click="tasks.filter = 'done'">Done <span>{{ tasks.counts.done }}</span></button>
          <button :class="{ active: tasks.filter === 'archived' }" @click="tasks.filter = 'archived'">Archived <span>{{ tasks.counts.archived }}</span></button>
        </div>

        <div v-if="mode === 'notes'" class="folder-panel">
          <button class="primary-button" type="button" @click="createNote">
            <Plus :size="16" />
            New note
          </button>
          <form class="inline-form" @submit.prevent="createFolder">
            <input v-model="newFolderName" placeholder="New folder">
            <button class="icon-button" type="submit" title="Create folder">
              <FolderPlus :size="16" />
            </button>
          </form>
          <button class="folder-row" type="button" :class="{ selected: !notes.selectedFolderClientId }" @click="notes.selectedFolderClientId = null">
            <Folder :size="15" />
            All root notes
          </button>
          <button
            v-for="folder in notes.visibleFolders"
            :key="folder.clientId"
            class="folder-row"
            type="button"
            :class="{ selected: notes.selectedFolderClientId === folder.clientId }"
            @click="notes.selectedFolderClientId = folder.clientId"
          >
            <Folder :size="15" />
            {{ folder.name }}
          </button>
          <button v-if="selectedFolder" class="text-button" type="button" @click="notes.selectedFolderClientId = selectedFolder.parentClientId">
            Back to parent
          </button>
          <div v-if="selectedFolder" class="folder-edit">
            <input v-model="renameFolderName" @change="renameSelectedFolder">
            <button class="danger-button" type="button" @click="deleteSelectedFolder">
              <Trash2 :size="14" />
              Delete folder
            </button>
          </div>
        </div>
      </template>
    </section>

    <section class="task-surface">
      <div class="surface-header">
        <div>
          <h2>{{ mode === 'tasks' ? 'Task list' : 'Notes workspace' }}</h2>
          <p>{{ auth.isAuthenticated ? `Last sync: ${lastSyncText}` : 'Sign in to load your data.' }}</p>
        </div>
        <div v-if="auth.isAuthenticated" class="surface-tools">
          <label class="search-field">
            <Search :size="16" />
            <input v-if="mode === 'tasks'" v-model="tasks.searchQuery" type="search" placeholder="Search tasks">
            <input v-else v-model="notes.searchQuery" type="search" placeholder="Search notes, tags, folders">
          </label>
          <button class="secondary-button" type="button" title="Sync" @click="safeRefresh">
            <RefreshCw :size="16" :class="{ spinning: tasks.isLoading || notes.isLoading }" />
            Sync
          </button>
        </div>
      </div>

      <div v-if="!auth.isAuthenticated" class="empty-state">
        <Clock3 :size="28" />
        <p>Login or register to use Kairos.</p>
      </div>

      <div v-else-if="mode === 'tasks'" class="task-layout">
        <div class="task-list" aria-label="Tasks">
          <button
            v-for="task in displayedTasks"
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
                <span v-for="tag in notes.tagsForTask(task.clientId)" :key="tag.clientId">#{{ tag.name }}</span>
              </span>
            </span>
            <span class="row-tags">
              <span v-if="task.isHighPriority" class="tag priority"><Star :size="12" />High</span>
              <span v-if="task.isArchived" class="tag">Archived</span>
            </span>
          </button>
          <div v-if="displayedTasks.length === 0" class="empty-state compact">
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
            <textarea :value="selectedTask.description" rows="8" @change="patch(selectedTask, { description: ($event.target as HTMLTextAreaElement).value })" />
          </label>
          <div class="field-grid">
            <label>
              Reminder
              <input type="datetime-local" :value="toLocalDateTimeValue(selectedTask.reminderTime)" @change="patch(selectedTask, { reminderTime: toIsoOrNull(($event.target as HTMLInputElement).value) })">
            </label>
            <label>
              Repeat
              <select :value="selectedTask.recurrence" @change="patch(selectedTask, { recurrence: ($event.target as HTMLSelectElement).value })">
                <option value="NONE">None</option>
                <option value="DAILY">Daily</option>
                <option value="WEEKLY">Weekly</option>
              </select>
            </label>
          </div>
          <div class="tag-editor">
            <span v-for="link in taskTagLinks(selectedTask)" :key="link.clientId" class="tag">
              #{{ tagName(link.tagClientId) }}
              <button type="button" @click="removeTaskTag(link)"><X :size="12" /></button>
            </span>
            <form class="inline-form" @submit.prevent="addTagToSelectedTask">
              <input v-model="taskTagDraft" placeholder="Add tag">
              <button class="secondary-button" type="submit"><Tag :size="14" />Add</button>
            </form>
          </div>
          <div class="toggle-list">
            <label class="check-row"><input type="checkbox" :checked="selectedTask.isHighPriority" @change="patch(selectedTask, { isHighPriority: ($event.target as HTMLInputElement).checked })">High priority</label>
            <label class="check-row"><input type="checkbox" :checked="selectedTask.isFullScreenReminder" @change="patch(selectedTask, { isFullScreenReminder: ($event.target as HTMLInputElement).checked })">Full-screen reminder</label>
            <label class="check-row"><input type="checkbox" :checked="selectedTask.isOneOffTask" @change="patch(selectedTask, { isOneOffTask: ($event.target as HTMLInputElement).checked })">Delete after completion</label>
          </div>
          <div class="detail-actions">
            <button class="secondary-button" type="button" @click="toggleComplete(selectedTask)"><Check :size="16" />{{ selectedTask.isCompleted ? 'Mark pending' : 'Complete' }}</button>
            <button class="secondary-button" type="button" @click="archiveTask(selectedTask)"><Archive :size="16" />{{ selectedTask.isArchived ? 'Unarchive' : 'Archive' }}</button>
            <button class="danger-button" type="button" @click="requestDeleteTask(selectedTask)"><Trash2 :size="16" />Delete</button>
          </div>
        </aside>
      </div>

      <div v-else class="notes-layout">
        <div class="note-list">
          <div class="breadcrumb">
            <button class="text-button" type="button" @click="notes.selectedFolderClientId = null">Root</button>
            <span v-for="folder in notes.folderPath" :key="folder.clientId">/ {{ folder.name }}</span>
          </div>
          <button
            v-for="note in notes.visibleNotes"
            :key="note.clientId"
            type="button"
            class="note-row"
            :class="{ selected: notes.selectedNoteClientId === note.clientId }"
            @click="notes.selectedNoteClientId = note.clientId"
          >
            <strong>{{ note.title }}</strong>
            <small>{{ note.markdownBody || 'Empty note' }}</small>
            <span class="meta-line">
              <span v-for="tag in notes.tagsForNote(note.clientId)" :key="tag.clientId">#{{ tag.name }}</span>
            </span>
          </button>
          <div v-if="notes.visibleNotes.length === 0" class="empty-state compact">
            <p>No notes in this view.</p>
          </div>
        </div>

        <aside v-if="notes.selectedNote" class="note-editor">
          <div class="detail-actions">
            <input class="title-input" :value="notes.selectedNote.title" @change="patchNote(notes.selectedNote, { title: ($event.target as HTMLInputElement).value })">
            <button class="secondary-button" type="button" @click="previewNote = !previewNote">
              <Eye v-if="!previewNote" :size="16" />
              <Pencil v-else :size="16" />
              {{ previewNote ? 'Edit' : 'Preview' }}
            </button>
          </div>
          <div class="tag-editor">
            <span v-for="link in noteTagLinks(notes.selectedNote)" :key="link.clientId" class="tag">
              #{{ tagName(link.tagClientId) }}
              <button type="button" @click="removeNoteTag(link)"><X :size="12" /></button>
            </span>
            <form class="inline-form" @submit.prevent="addTagToSelectedNote">
              <input v-model="tagDraft" placeholder="Add tag">
              <button class="secondary-button" type="submit"><Tag :size="14" />Add</button>
            </form>
          </div>
          <textarea
            v-if="!previewNote"
            class="markdown-editor"
            :value="notes.selectedNote.markdownBody"
            rows="18"
            placeholder="Write Markdown. Use [[task:id|Title]] or insert links below."
            @change="patchNote(notes.selectedNote, { markdownBody: ($event.target as HTMLTextAreaElement).value })"
          />
          <article v-else class="markdown-preview" v-html="renderMarkdown(notes.selectedNote.markdownBody)" />
          <div class="reference-panel">
            <div>
              <h3>Linked tasks</h3>
              <button v-for="task in linkedTasksForSelectedNote" :key="task.clientId" type="button" class="mini-row" @click="mode = 'tasks'; selectedTaskId = task.id">
                <Link2 :size="13" />
                {{ task.title }}
              </button>
              <button v-for="task in tasks.visibleTasks.slice(0, 6)" :key="`insert-${task.clientId}`" type="button" class="mini-row muted-row" @click="insertTaskReference(task)">
                Insert task: {{ task.title }}
              </button>
            </div>
            <div>
              <h3>Backlinks</h3>
              <button v-for="note in noteBacklinks" :key="note.clientId" type="button" class="mini-row" @click="notes.selectedNoteClientId = note.clientId">
                <FileText :size="13" />
                {{ note.title }}
              </button>
              <button v-for="note in notes.activeNotes.filter((item) => item.clientId !== notes.selectedNote?.clientId).slice(0, 6)" :key="`note-${note.clientId}`" type="button" class="mini-row muted-row" @click="insertNoteReference(note)">
                Insert note: {{ note.title }}
              </button>
            </div>
          </div>
          <div class="detail-actions">
            <select :value="notes.selectedNote.folderClientId || ''" @change="patchNote(notes.selectedNote, { folderClientId: (($event.target as HTMLSelectElement).value || null) })">
              <option value="">Root</option>
              <option v-for="folder in notes.activeFolders" :key="folder.clientId" :value="folder.clientId">{{ folder.name }}</option>
            </select>
            <button class="danger-button" type="button" @click="deleteSelectedNote"><Trash2 :size="16" />Delete note</button>
          </div>
        </aside>
      </div>
    </section>

    <Transition name="modal">
      <div v-if="pendingDeleteTask" class="modal-backdrop" @click.self="pendingDeleteTask = null">
        <div class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-task-title">
          <h2 id="delete-task-title">Delete task?</h2>
          <p>This removes "{{ pendingDeleteTask.title }}" from your task list. You can undo right after deleting.</p>
          <div class="confirm-actions">
            <button class="secondary-button" type="button" @click="pendingDeleteTask = null">Cancel</button>
            <button class="danger-button" type="button" @click="confirmDeleteTask"><Trash2 :size="16" />Delete</button>
          </div>
        </div>
      </div>
    </Transition>

    <Transition name="toast">
      <div v-if="toast" class="toast">
        <span>{{ toast.message }}</span>
        <button v-if="toast.action" type="button" @click="runToastAction">{{ toast.actionLabel || 'Undo' }}</button>
      </div>
    </Transition>
  </main>
</template>
