import { ApiError } from '@/lib/api/api-error'
import { apiRequest } from '@/lib/api/client'
import type { CurrentUser, LoginRequest } from '@/lib/api/contracts'
import { clearCsrfToken, getCsrfToken } from '@/lib/api/csrf-store'
import { apiPaths } from '@/lib/api/paths'

export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    return await apiRequest<CurrentUser>(apiPaths.auth.me)
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) return null
    throw error
  }
}

export async function login(request: LoginRequest): Promise<CurrentUser> {
  const csrfToken = await getCsrfToken()
  try {
    return await apiRequest<CurrentUser>(apiPaths.auth.login, {
      method: 'POST',
      body: request,
      csrfToken,
    })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      throw new ApiError('Email or password is incorrect.', 401, 'AUTHENTICATION_REQUIRED')
    }
    throw error
  }
}

export async function logout(): Promise<void> {
  const csrfToken = await getCsrfToken()
  await apiRequest<void>(apiPaths.auth.logout, { method: 'POST', csrfToken })
  clearCsrfToken()
}
