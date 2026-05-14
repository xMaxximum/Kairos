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
