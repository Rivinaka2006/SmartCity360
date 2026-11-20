import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, type PropsWithChildren } from 'react'
import { setUnauthorizedHandler } from '@/lib/api/client'
import { toast } from 'sonner'
import type { CurrentUser, LoginRequest } from '@/lib/api/contracts'
import { clearCsrfToken } from '@/lib/api/csrf-store'
import { fetchCurrentUser, login as loginRequest, logout as logoutRequest } from './auth-api'
import { AuthContext, authQueryKey, type AuthContextValue } from './auth-state'

export function AuthProvider({ children }: PropsWithChildren) {
  const queryClient = useQueryClient()
  const session = useQuery({
    queryKey: authQueryKey,
    queryFn: fetchCurrentUser,
    staleTime: 60_000,
  })

  const clearSession = useCallback(() => {
    const wasAuthenticated = Boolean(queryClient.getQueryData<CurrentUser>(authQueryKey))
    clearCsrfToken()
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== 'auth' })
    queryClient.setQueryData<CurrentUser | null>(authQueryKey, null)
    if (wasAuthenticated) toast.error('Your session expired. Please sign in again.')
  }, [queryClient])

  useEffect(() => {
    setUnauthorizedHandler(clearSession)
    return () => setUnauthorizedHandler(undefined)
  }, [clearSession])

  const login = useCallback(async (request: LoginRequest) => {
    const user = await loginRequest(request)
    queryClient.setQueryData<CurrentUser>(authQueryKey, user)
    return user
  }, [queryClient])

  const logout = useCallback(async () => {
    await logoutRequest()
    clearSession()
  }, [clearSession])

  const value = useMemo<AuthContextValue>(() => {
    if (session.isPending) return { status: 'loading', user: null, error: null, login, logout }
    if (session.isError) return {
      status: 'error',
      user: null,
      error: session.error instanceof Error ? session.error : new Error('Session initialization failed'),
      login,
      logout,
    }
    if (!session.data) return { status: 'anonymous', user: null, error: null, login, logout }
    return { status: 'authenticated', user: session.data, error: null, login, logout }
  }, [login, logout, session.data, session.error, session.isError, session.isPending])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
