export interface AuthUser {
  id: string
  email: string
}

export interface AuthTokens {
  accessToken: string
  accessTokenExpiresAt: string
  refreshToken: string
  refreshTokenExpiresAt: string
  user: AuthUser
}

export interface TaskItem {
  id: string
  clientId: string
  title: string
  description: string
  createdAt: string
  updatedAt: string
  deletedAt: string | null
  reminderTime: string | null
  recurrence: 'NONE' | 'DAILY' | 'WEEKLY' | string
  isHighPriority: boolean
  isFullScreenReminder: boolean
  attachments: string[]
  isCompleted: boolean
  isArchived: boolean
  isOneOffTask: boolean
}

export interface CreateTaskRequest {
  clientId: string
  title: string
  description?: string
  reminderTime?: string | null
  recurrence?: string
  isHighPriority: boolean
  isFullScreenReminder: boolean
  attachments: string[]
  isCompleted: boolean
  isArchived: boolean
  isOneOffTask: boolean
}

export interface NoteItem {
  id: string
  clientId: string
  folderClientId: string | null
  title: string
  markdownBody: string
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface NoteFolder {
  id: string
  clientId: string
  parentClientId: string | null
  name: string
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface TagItem {
  id: string
  clientId: string
  name: string
  normalizedName: string
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface NoteTagLink {
  id: string
  clientId: string
  noteClientId: string
  tagClientId: string
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface TaskTagLink {
  id: string
  clientId: string
  taskClientId: string
  tagClientId: string
  createdAt: string
  updatedAt: string
  deletedAt: string | null
}

export interface NoteTaskReference {
  noteClientId: string
  taskClientId: string
}

export interface NoteNoteReference {
  sourceNoteClientId: string
  targetNoteClientId: string
}

export interface NotesSyncResponse {
  notes: NoteItem[]
  folders: NoteFolder[]
  tags: TagItem[]
  noteTagLinks: NoteTagLink[]
  taskTagLinks: TaskTagLink[]
  noteTaskReferences: NoteTaskReference[]
  noteNoteReferences: NoteNoteReference[]
  cursor: string
}
