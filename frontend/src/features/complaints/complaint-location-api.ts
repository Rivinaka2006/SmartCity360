import { apiRequest } from '@/lib/api/client'

export type ComplaintLocation = {
  id: string
  name: string
  code: string
  type: string
}

const root = '/smartcity360/api/v1/zones/complaint-locations'

export function listComplaintLocations(search: string, offset: number, limit: number, signal?: AbortSignal) {
  const query = new URLSearchParams({ search, offset: String(offset), limit: String(limit) })
  return apiRequest<ComplaintLocation[]>(`${root}?${query}`, { signal })
}
