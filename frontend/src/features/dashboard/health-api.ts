import { apiRequest } from '@/lib/api/client'
import type { HealthStatus } from '@/lib/api/contracts'
import { apiPaths } from '@/lib/api/paths'

export const healthQueryKey = ['health'] as const

export function fetchHealth(signal?: AbortSignal) {
  return apiRequest<HealthStatus>(apiPaths.health, { signal })
}
