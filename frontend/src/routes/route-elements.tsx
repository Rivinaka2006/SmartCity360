import type { PropsWithChildren } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { LoginPage } from '@/features/auth/login-page'
import { useAuth } from '@/features/auth/use-auth'
import type { SmartCityRole } from '@/lib/api/contracts'
import { AppShell } from '@/components/layout/app-shell'
import { ProtectedRoute } from './protected-route'
import { sanitizeReturnPath } from './route-policy'

export function LoginRoute() {
  const auth = useAuth()
  const location = useLocation()
  if (auth.status === 'authenticated') {
    const state = location.state as { returnTo?: unknown } | null
    return <Navigate to={sanitizeReturnPath(state?.returnTo)} replace />
  }
  return <LoginPage />
}

export function ProtectedApplication() {
  return <ProtectedRoute><AppShell><Outlet /></AppShell></ProtectedRoute>
}

export function RoleRoute({ roles, children }: PropsWithChildren<{ roles: readonly SmartCityRole[] }>) {
  return <ProtectedRoute allowedRoles={roles}>{children ?? <Outlet />}</ProtectedRoute>
}
