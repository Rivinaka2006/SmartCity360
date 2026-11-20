import { apiRequest } from '@/lib/api/client'
import { apiMutation } from '@/lib/api/mutations'

export type Zone = {
  id: string
  parentId: string | null
  type: string
  code: string
  name: string
  status: string
  createdAt: string
  updatedAt: string
}

export type ZoneTree = {
  id: string
  type: string
  code: string
  name: string
  children: ZoneTree[]
}

export type ZoneCommand = {
  id: string
  targetZoneId: string
  issuedBy: string
  command: string
  status: string
  affectedBuildings: number
  message: string
  issuedAt: string
}

export type ZoneUsage = {
  targetZoneId: string
  from: string
  to: string
  totalKwh: number
  readingCount: number
  meterCount: number
  buildingsWithReadings: number
  buildingsWithoutReadings: number
  inclusionRule: string
}

const root = '/smartcity360/api/v1/zones'
const query = (params: Record<string, string | number>) => new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)]))

export function listZones(offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Zone[]>(`${root}?${query({ offset, limit })}`, { signal })
}

export function listZoneParents(type: string, search: string, offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Zone[]>(`${root}/parents?${query({ type, search, offset, limit })}`, { signal })
}

export function getZone(id: string, signal?: AbortSignal) {
  return apiRequest<Zone>(`${root}/${encodeURIComponent(id)}`, { signal })
}

export function getZoneTree(id: string, signal?: AbortSignal) {
  return apiRequest<ZoneTree>(`${root}/${encodeURIComponent(id)}/tree`, { signal })
}

export function getZonePath(id: string, signal?: AbortSignal) {
  return apiRequest<Zone[]>(`${root}/${encodeURIComponent(id)}/path`, { signal })
}

export function getZoneCommands(id: string, offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<ZoneCommand[]>(`${root}/${encodeURIComponent(id)}/commands?${query({ offset, limit })}`, { signal })
}

export function getZoneUsage(id: string, from: string, to: string, signal?: AbortSignal) {
  return apiRequest<ZoneUsage>(`${root}/${encodeURIComponent(id)}/usage?${query({ from, to })}`, { signal })
}

export const createZone = (body: { type: string; code: string; name: string; parentId: string | null }) =>
  apiMutation<Zone>(root, { method: 'POST', body })

export const updateZone = (id: string, body: { code: string; name: string; parentId: string | null }) =>
  apiMutation<Zone>(`${root}/${encodeURIComponent(id)}`, { method: 'PUT', body })

export const runZonePowerCommand = (id: string, command: 'shutdown' | 'restore') =>
  apiMutation<{ commandId: string; targetZoneId: string; command: string; targetedBuildings: number; changedBuildings: number; message: string; executedAt: string }>(
    `${root}/${encodeURIComponent(id)}/${command}`,
    { method: 'POST' },
  )
