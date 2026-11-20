import { createContext } from 'react'
import type { CurrentUser, LoginRequest } from '@/lib/api/contracts'

export type AuthStatus = 'loading' | 'anonymous' | 'authenticated' | 'error'

export type AuthContextValue = {
  status: AuthStatus
  user: CurrentUser | null
  error: Error | null
  login: (request: LoginRequest) => Promise<CurrentUser>
  logout: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
export const authQueryKey = ['auth', 'me'] as const
