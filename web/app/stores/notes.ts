import { defineStore } from 'pinia'
import type {
  NoteFolder,
  NoteItem,
  NoteNoteReference,
  NoteTagLink,
  NoteTaskReference,
  NotesSyncResponse,
  TagItem,
  TaskTagLink
} from '~/types/kairos'

const CACHE_KEY = 'kairos.notes.cache'

export const useNotesStore = defineStore('notes', () => {
  const auth = useAuthStore()
  const notes = ref<NoteItem[]>([])
  const folders = ref<NoteFolder[]>([])
  const tags = ref<TagItem[]>([])
  const noteTagLinks = ref<NoteTagLink[]>([])
  const taskTagLinks = ref<TaskTagLink[]>([])
  const noteTaskReferences = ref<NoteTaskReference[]>([])
  const noteNoteReferences = ref<NoteNoteReference[]>([])
  const selectedFolderClientId = ref<string | null>(null)
  const selectedNoteClientId = ref<string | null>(null)
  const searchQuery = ref('')
  const isLoading = ref(false)
  const isSaving = ref(false)
  const message = ref('')
  const lastSyncedAt = ref<number | null>(null)

  const activeFolders = computed(() => folders.value.filter((folder) => !folder.deletedAt))
  const activeNotes = computed(() => notes.value.filter((note) => !note.deletedAt))
  const activeTags = computed(() => tags.value.filter((tag) => !tag.deletedAt))

  const folderPath = computed(() => {
    const path: NoteFolder[] = []
    let currentId = selectedFolderClientId.value
    const byClientId = new Map(activeFolders.value.map((folder) => [folder.clientId, folder]))
    while (currentId) {
      const folder = byClientId.get(currentId)
      if (!folder) break
      path.unshift(folder)
      currentId = folder.parentClientId
    }
    return path
  })

  const selectedNote = computed(() => {
    return activeNotes.value.find((note) => note.clientId === selectedNoteClientId.value) || null
  })

  const visibleFolders = computed(() => {
    return activeFolders.value
      .filter((folder) => folder.parentClientId === selectedFolderClientId.value)
      .sort((a, b) => a.name.localeCompare(b.name))
  })

  const visibleNotes = computed(() => {
    const query = searchQuery.value.trim().toLowerCase()
    return activeNotes.value
      .filter((note) => {
        if (!query) return note.folderClientId === selectedFolderClientId.value
        return searchableText(note).includes(query)
      })
      .sort((a, b) => Date.parse(b.updatedAt) - Date.parse(a.updatedAt))
  })

  function hydrate() {
    if (!import.meta.client) return
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return
    try {
      const parsed = JSON.parse(raw) as NotesSyncResponse & { lastSyncedAt: number | null }
      notes.value = parsed.notes || []
      folders.value = parsed.folders || []
      tags.value = parsed.tags || []
      noteTagLinks.value = parsed.noteTagLinks || []
      taskTagLinks.value = parsed.taskTagLinks || []
      noteTaskReferences.value = parsed.noteTaskReferences || []
      noteNoteReferences.value = parsed.noteNoteReferences || []
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
        notes: notes.value,
        folders: folders.value,
        tags: tags.value,
        noteTagLinks: noteTagLinks.value,
        taskTagLinks: taskTagLinks.value,
        noteTaskReferences: noteTaskReferences.value,
        noteNoteReferences: noteNoteReferences.value,
        lastSyncedAt: lastSyncedAt.value
      })
    )
  }

  async function fetchAll() {
    if (!auth.isAuthenticated) return
    isLoading.value = true
    message.value = ''
    try {
      const sync = await auth.authFetch<NotesSyncResponse>('/notes/sync')
      notes.value = sync.notes || []
      folders.value = sync.folders || []
      tags.value = sync.tags || []
      noteTagLinks.value = sync.noteTagLinks || []
      taskTagLinks.value = sync.taskTagLinks || []
      noteTaskReferences.value = sync.noteTaskReferences || []
      noteNoteReferences.value = sync.noteNoteReferences || []
      lastSyncedAt.value = Date.now()
      persist()
      message.value = 'Notes synced.'
    } catch (error) {
      message.value = readableError(error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function createNote(title = 'Untitled note') {
    isSaving.value = true
    try {
      const note = await auth.authFetch<NoteItem>('/notes', {
        method: 'POST',
        body: {
          clientId: crypto.randomUUID(),
          folderClientId: selectedFolderClientId.value,
          title,
          markdownBody: ''
        }
      })
      upsertNote(note)
      selectedNoteClientId.value = note.clientId
      return note
    } finally {
      isSaving.value = false
    }
  }

  async function patchNote(note: NoteItem, patch: Partial<Pick<NoteItem, 'title' | 'markdownBody' | 'folderClientId'>>) {
    const updated = await auth.authFetch<NoteItem>(`/notes/${note.id}`, {
      method: 'PATCH',
      body: {
        ...patch,
        folderClientId: Object.prototype.hasOwnProperty.call(patch, 'folderClientId') ? patch.folderClientId : note.folderClientId,
        baseUpdatedAt: note.updatedAt
      }
    })
    upsertNote(updated)
    return updated
  }

  async function deleteNote(note: NoteItem) {
    const deleted = await auth.authFetch<NoteItem>(`/notes/${note.id}?baseUpdatedAt=${encodeURIComponent(note.updatedAt)}`, {
      method: 'DELETE'
    })
    upsertNote(deleted)
    if (selectedNoteClientId.value === note.clientId) selectedNoteClientId.value = null
    return deleted
  }

  async function createFolder(name: string, parentClientId = selectedFolderClientId.value) {
    const folder = await auth.authFetch<NoteFolder>('/note-folders', {
      method: 'POST',
      body: {
        clientId: crypto.randomUUID(),
        parentClientId,
        name: name.trim()
      }
    })
    upsertFolder(folder)
    return folder
  }

  async function patchFolder(folder: NoteFolder, patch: Partial<Pick<NoteFolder, 'name' | 'parentClientId'>>) {
    const updated = await auth.authFetch<NoteFolder>(`/note-folders/${folder.id}`, {
      method: 'PATCH',
      body: {
        ...patch,
        parentClientId: Object.prototype.hasOwnProperty.call(patch, 'parentClientId') ? patch.parentClientId : folder.parentClientId,
        baseUpdatedAt: folder.updatedAt
      }
    })
    upsertFolder(updated)
    return updated
  }

  async function deleteFolder(folder: NoteFolder) {
    const deleted = await auth.authFetch<NoteFolder>(`/note-folders/${folder.id}?baseUpdatedAt=${encodeURIComponent(folder.updatedAt)}`, {
      method: 'DELETE'
    })
    upsertFolder(deleted)
    if (selectedFolderClientId.value === folder.clientId) selectedFolderClientId.value = folder.parentClientId
    return deleted
  }

  async function ensureTag(name: string) {
    const normalized = normalizeTag(name)
    const existing = activeTags.value.find((tag) => tag.normalizedName === normalized)
    if (existing) return existing
    const tag = await auth.authFetch<TagItem>('/tags', {
      method: 'POST',
      body: {
        clientId: crypto.randomUUID(),
        name: name.trim()
      }
    })
    upsertTag(tag)
    return tag
  }

  async function addNoteTag(note: NoteItem, name: string) {
    const tag = await ensureTag(name)
    const existing = noteTagLinks.value.find((link) => !link.deletedAt && link.noteClientId === note.clientId && link.tagClientId === tag.clientId)
    if (existing) return existing
    const link = await auth.authFetch<NoteTagLink>('/note-tag-links', {
      method: 'POST',
      body: {
        clientId: crypto.randomUUID(),
        noteClientId: note.clientId,
        tagClientId: tag.clientId
      }
    })
    upsertNoteTagLink(link)
    return link
  }

  async function addTaskTag(taskClientId: string, name: string) {
    const tag = await ensureTag(name)
    const existing = taskTagLinks.value.find((link) => !link.deletedAt && link.taskClientId === taskClientId && link.tagClientId === tag.clientId)
    if (existing) return existing
    const link = await auth.authFetch<TaskTagLink>('/task-tag-links', {
      method: 'POST',
      body: {
        clientId: crypto.randomUUID(),
        taskClientId,
        tagClientId: tag.clientId
      }
    })
    upsertTaskTagLink(link)
    return link
  }

  async function removeNoteTag(link: NoteTagLink) {
    const deleted = await auth.authFetch<NoteTagLink>(`/note-tag-links/${link.id}?baseUpdatedAt=${encodeURIComponent(link.updatedAt)}`, {
      method: 'DELETE'
    })
    upsertNoteTagLink(deleted)
    return deleted
  }

  async function removeTaskTag(link: TaskTagLink) {
    const deleted = await auth.authFetch<TaskTagLink>(`/task-tag-links/${link.id}?baseUpdatedAt=${encodeURIComponent(link.updatedAt)}`, {
      method: 'DELETE'
    })
    upsertTaskTagLink(deleted)
    return deleted
  }

  function tagsForNote(noteClientId: string) {
    const tagIds = noteTagLinks.value.filter((link) => !link.deletedAt && link.noteClientId === noteClientId).map((link) => link.tagClientId)
    return activeTags.value.filter((tag) => tagIds.includes(tag.clientId))
  }

  function tagsForTask(taskClientId: string) {
    const tagIds = taskTagLinks.value.filter((link) => !link.deletedAt && link.taskClientId === taskClientId).map((link) => link.tagClientId)
    return activeTags.value.filter((tag) => tagIds.includes(tag.clientId))
  }

  function taskLinksForNote(noteClientId: string) {
    return noteTaskReferences.value.filter((reference) => reference.noteClientId === noteClientId)
  }

  function noteBacklinks(noteClientId: string) {
    return noteNoteReferences.value.filter((reference) => reference.targetNoteClientId === noteClientId)
  }

  function notesInFolderTree(folderClientId: string | null) {
    const folderIds = new Set<string | null>([folderClientId])
    let changed = true
    while (changed) {
      changed = false
      activeFolders.value.forEach((folder) => {
        if (folder.parentClientId && folderIds.has(folder.parentClientId) && !folderIds.has(folder.clientId)) {
          folderIds.add(folder.clientId)
          changed = true
        }
      })
    }
    return activeNotes.value.filter((note) => folderIds.has(note.folderClientId))
  }

  function upsertNote(note: NoteItem) {
    upsertByClientId(notes.value, note)
    persist()
  }

  function upsertFolder(folder: NoteFolder) {
    upsertByClientId(folders.value, folder)
    persist()
  }

  function upsertTag(tag: TagItem) {
    upsertByClientId(tags.value, tag)
    persist()
  }

  function upsertNoteTagLink(link: NoteTagLink) {
    upsertByClientId(noteTagLinks.value, link)
    persist()
  }

  function upsertTaskTagLink(link: TaskTagLink) {
    upsertByClientId(taskTagLinks.value, link)
    persist()
  }

  function searchableText(note: NoteItem) {
    const tagText = tagsForNote(note.clientId).map((tag) => tag.name).join(' ')
    const folderText = activeFolders.value.find((folder) => folder.clientId === note.folderClientId)?.name || ''
    return `${note.title} ${note.markdownBody} ${tagText} ${folderText}`.toLowerCase()
  }

  return {
    notes,
    folders,
    tags,
    noteTagLinks,
    taskTagLinks,
    noteTaskReferences,
    noteNoteReferences,
    activeFolders,
    activeNotes,
    activeTags,
    selectedFolderClientId,
    selectedNoteClientId,
    selectedNote,
    visibleFolders,
    visibleNotes,
    folderPath,
    searchQuery,
    isLoading,
    isSaving,
    message,
    lastSyncedAt,
    hydrate,
    fetchAll,
    createNote,
    patchNote,
    deleteNote,
    createFolder,
    patchFolder,
    deleteFolder,
    ensureTag,
    addNoteTag,
    addTaskTag,
    removeNoteTag,
    removeTaskTag,
    tagsForNote,
    tagsForTask,
    taskLinksForNote,
    noteBacklinks,
    notesInFolderTree
  }
})

function upsertByClientId<T extends { clientId: string }>(items: T[], item: T) {
  const index = items.findIndex((existing) => existing.clientId === item.clientId)
  if (index >= 0) {
    items[index] = item
  } else {
    items.unshift(item)
  }
}

function normalizeTag(name: string) {
  return name.trim().toUpperCase()
}

function readableError(error: any) {
  const data = error?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data?.error) return data.error
  if (error?.message) return error.message
  return 'Request failed.'
}
