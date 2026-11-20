import type { SmartCityRole } from '@/lib/api/contracts'
import { apiRequest } from '@/lib/api/client'
import { apiMutation } from '@/lib/api/mutations'

export type ManagedUser = {
  id: string
  fullName: string
  email: string
  status: 'ACTIVE' | 'SUSPENDED' | 'DISABLED'
  roles: SmartCityRole[]
  integrationTestAccount: boolean
  createdAt: string
  updatedAt: string
}

export const USERS_PER_PAGE = 2

const root = '/smartcity360/api/v1/users'

export function listManagedUsers(search: string, offset: number, limit: number, signal?: AbortSignal) {
  const query = new URLSearchParams({ search, offset: String(offset), limit: String(limit) })
  return apiRequest<ManagedUser[]>(`${root}?${query}`, { signal })
}

export function createManagedUser(body: {
  fullName: string
  email: string
  password: string
  role: SmartCityRole
  integrationTestAccount: boolean
}) {
  return apiMutation<ManagedUser>(root, { method: 'POST', body })
}

export function updateManagedUserRoles(id: string, roles: SmartCityRole[]) {
  return apiMutation<ManagedUser>(`${root}/${encodeURIComponent(id)}/roles`, { method: 'PUT', body: { roles } })
}

export function updateManagedUserStatus(id: string, status: ManagedUser['status']) {
  return apiMutation<ManagedUser>(`${root}/${encodeURIComponent(id)}/status`, { method: 'PUT', body: { status } })
}
