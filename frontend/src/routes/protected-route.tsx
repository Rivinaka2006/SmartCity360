import type { PropsWithChildren } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { UnauthorizedView } from '@/components/common/unauthorized-view'
import { useAuth } from '@/features/auth/use-auth'
import type { SmartCityRole } from '@/lib/api/contracts'
import { sanitizeReturnPath } from './route-policy'

type ProtectedRouteProps = PropsWithChildren<{
  allowedRoles?: readonly SmartCityRole[]
}>

export function ProtectedRoute({ allowedRoles, children }: ProtectedRouteProps) {
  const auth = useAuth()
  const location = useLocation()

  if (auth.status === 'loading') return <div role="status" className="loading-state">Checking your session…</div>
  if (auth.status === 'error') return <div role="alert" className="error-state">Your session could not be checked. Please retry.</div>
  if (auth.status === 'anonymous') {
    const returnTo = sanitizeReturnPath(`${location.pathname}${location.search}${location.hash}`)
    return <Navigate to="/login" replace state={{ returnTo, reason: 'authentication-required' }} />
  }
  if (allowedRoles && !auth.user?.roles.some((role) => allowedRoles.includes(role))) return <UnauthorizedView />
  return children
}
