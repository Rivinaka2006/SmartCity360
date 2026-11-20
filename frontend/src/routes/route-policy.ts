import type { SmartCityRole } from '@/lib/api/contracts'

export type AppRouteId = 'dashboard' | 'zones' | 'complaints' | 'streetlights' | 'energy' | 'policies' | 'users'

export type AppRouteDefinition = {
  id: AppRouteId
  label: string
  path: string
  allowedRoles: readonly SmartCityRole[]
}

const ALL_ROLES: readonly SmartCityRole[] = ['ADMIN', 'CITY_MANAGER', 'SUPERVISOR', 'LOCAL_STAFF', 'RESIDENT']
const OPERATIONS_ROLES: readonly SmartCityRole[] = ['ADMIN', 'CITY_MANAGER', 'SUPERVISOR', 'LOCAL_STAFF']

export const appRoutes: readonly AppRouteDefinition[] = [
  { id: 'dashboard', label: 'Dashboard', path: '/app', allowedRoles: ALL_ROLES },
  { id: 'zones', label: 'Zones', path: '/app/zones', allowedRoles: OPERATIONS_ROLES },
  { id: 'complaints', label: 'Complaints', path: '/app/complaints', allowedRoles: ALL_ROLES },
  { id: 'streetlights', label: 'Streetlights', path: '/app/streetlights', allowedRoles: OPERATIONS_ROLES },
  { id: 'energy', label: 'Energy streams', path: '/app/energy', allowedRoles: ['ADMIN', 'CITY_MANAGER', 'SUPERVISOR'] },
  { id: 'policies', label: 'Policies', path: '/app/policies', allowedRoles: OPERATIONS_ROLES },
  { id: 'users', label: 'Users', path: '/app/users', allowedRoles: ['ADMIN'] },
]

export function canAccessRoute(roles: readonly SmartCityRole[], route: AppRouteDefinition) {
  return roles.some((role) => route.allowedRoles.includes(role))
}

export function availableNavigation(roles: readonly SmartCityRole[]) {
  return appRoutes.filter((route) => canAccessRoute(roles, route))
}

export const availableAreas = availableNavigation

export function sanitizeReturnPath(candidate: unknown) {
  if (typeof candidate !== 'string') return '/app'
  if (!candidate.startsWith('/app') || candidate.startsWith('//') || candidate.includes('\\')) return '/app'
  if (candidate !== '/app' && !candidate.startsWith('/app/') && !candidate.startsWith('/app?') && !candidate.startsWith('/app#')) return '/app'
  return candidate
}
