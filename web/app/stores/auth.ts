import { defineStore } from 'pinia'
import type { AuthTokens, AuthUser } from '~/types/kairos'

const STORAGE_KEY = 'kairos.auth'

interface StoredAuth {
  accessToken: string
  refreshToken: string
  user: AuthUser
}

export const useAuthStore = defineStore('auth', () => {
  const config = useRuntimeConfig()
  const accessToken = ref('')
  const refreshToken = ref('')
  const user = ref<AuthUser | null>(null)
  const isLoading = ref(false)
  const message = ref('')

  const isAuthenticated = computed(() => Boolean(accessToken.value && user.value))

  function hydrate() {
    if (!import.meta.client) return
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return
    try {
      const stored = JSON.parse(raw) as StoredAuth
      accessToken.value = stored.accessToken || ''
      refreshToken.value = stored.refreshToken || ''
      user.value = stored.user || null
    } catch {
      localStorage.removeItem(STORAGE_KEY)
    }
  }

  function persist() {
    if (!import.meta.client) return
    if (!accessToken.value || !refreshToken.value || !user.value) {
      localStorage.removeItem(STORAGE_KEY)
      return
    }
    localStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({
        accessToken: accessToken.value,
        refreshToken: refreshToken.value,
        user: user.value
      } satisfies StoredAuth)
    )
  }

  function applyTokens(tokens: AuthTokens) {
    accessToken.value = tokens.accessToken
    refreshToken.value = tokens.refreshToken
    user.value = tokens.user
    persist()
  }

  async function login(email: string, password: string) {
    await authenticate('login', email, password)
  }

  async function register(email: string, password: string) {
    await authenticate('register', email, password)
  }

  async function authenticate(mode: 'login' | 'register', email: string, password: string) {
    isLoading.value = true
    message.value = ''
    try {
      const tokens = await $fetch<AuthTokens>(`${config.public.apiBase}/auth/${mode}`, {
        method: 'POST',
        body: {
          email: email.trim(),
          password,
          deviceName: 'Kairos Web'
        }
      })
      applyTokens(tokens)
      message.value = mode === 'register' ? 'Account created.' : 'Signed in.'
    } catch (error) {
      message.value = readableError(error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  async function refresh() {
    if (!refreshToken.value) throw new Error('No refresh token.')
    const tokens = await $fetch<AuthTokens>(`${config.public.apiBase}/auth/refresh`, {
      method: 'POST',
      body: {
        refreshToken: refreshToken.value,
        deviceName: 'Kairos Web'
      }
    })
    applyTokens(tokens)
  }

  async function authFetch<T>(path: string, options: Parameters<typeof $fetch<T>>[1] = {}) {
    if (!accessToken.value) throw new Error('Sign in first.')
    try {
      return await $fetch<T>(`${config.public.apiBase}${path}`, {
        ...options,
        headers: {
          ...(options?.headers || {}),
          Authorization: `Bearer ${accessToken.value}`
        }
      })
    } catch (error: any) {
      if (error?.statusCode === 401 && refreshToken.value) {
        await refresh()
        return await $fetch<T>(`${config.public.apiBase}${path}`, {
          ...options,
          headers: {
            ...(options?.headers || {}),
            Authorization: `Bearer ${accessToken.value}`
          }
        })
      }
      throw error
    }
  }

  function logout() {
    accessToken.value = ''
    refreshToken.value = ''
    user.value = null
    message.value = ''
    persist()
  }

  return {
    accessToken,
    refreshToken,
    user,
    isLoading,
    isAuthenticated,
    message,
    hydrate,
    login,
    register,
    refresh,
    authFetch,
    logout
  }
})

function readableError(error: any) {
  const data = error?.data
  if (typeof data === 'string' && data.trim()) return data
  if (data?.error) return data.error
  if (error?.message) return error.message
  return 'Request failed.'
}
