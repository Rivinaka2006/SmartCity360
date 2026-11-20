import { apiRequest } from '@/lib/api/client'
import { ApiError } from '@/lib/api/api-error'
import { apiPaths } from '@/lib/api/paths'

export type DashboardSummary = {
  asOf: string
  zones: {
    total: number
    buildings: number
  }
  power: {
    on: number
    off: number
    unknown: number
  }
  complaints: {
    total: number
    open: number
    inProgress: number
    escalated: number
    resolved: number
    rejected: number
  }
  streetlights: {
    total: number
    on: number
    off: number
    fault: number
    maintenance: number
  }
  energy: {
    from: string
    to: string
    consumptionKwh: number | null
    readingCount: number
  }
  policies: {
    total: number
    draft: number
    active: number
    inactive: number
  }
}

export const dashboardQueryKey = ['dashboard-summary'] as const

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isFiniteCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value) && value >= 0
}

function isTimestamp(value: unknown): value is string {
  return typeof value === 'string' && Number.isFinite(Date.parse(value))
}

function isDashboardSummary(value: unknown): value is DashboardSummary {
  if (!isRecord(value) || !isTimestamp(value.asOf)) return false
  if (!isRecord(value.zones) || !isFiniteCount(value.zones.total) || !isFiniteCount(value.zones.buildings)) return false
  if (!isRecord(value.power) || !isFiniteCount(value.power.on) || !isFiniteCount(value.power.off) || !isFiniteCount(value.power.unknown)) return false
  if (!isRecord(value.complaints)
    || !isFiniteCount(value.complaints.total)
    || !isFiniteCount(value.complaints.open)
    || !isFiniteCount(value.complaints.inProgress)
    || !isFiniteCount(value.complaints.escalated)
    || !isFiniteCount(value.complaints.resolved)
    || !isFiniteCount(value.complaints.rejected)) return false
  if (!isRecord(value.streetlights)
    || !isFiniteCount(value.streetlights.total)
    || !isFiniteCount(value.streetlights.on)
    || !isFiniteCount(value.streetlights.off)
    || !isFiniteCount(value.streetlights.fault)
    || !isFiniteCount(value.streetlights.maintenance)) return false
  if (!isRecord(value.energy)
    || !isTimestamp(value.energy.from)
    || !isTimestamp(value.energy.to)
    || !isFiniteCount(value.energy.readingCount)
    || (value.energy.consumptionKwh !== null && !isFiniteCount(value.energy.consumptionKwh))) return false
  if (!isRecord(value.policies)
    || !isFiniteCount(value.policies.total)
    || !isFiniteCount(value.policies.draft)
    || !isFiniteCount(value.policies.active)
    || !isFiniteCount(value.policies.inactive)) return false
  return true
}

export function fetchDashboardSummary(signal?: AbortSignal) {
  return apiRequest<unknown>(apiPaths.dashboard.summary, { signal }).then((payload) => {
    if (!isDashboardSummary(payload)) {
      throw new ApiError('The dashboard returned an incompatible response.', 200, 'UNEXPECTED_RESPONSE')
    }
    return payload
  })
}
