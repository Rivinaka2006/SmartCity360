import { apiRequest } from '@/lib/api/client'
import { ApiError } from '@/lib/api/api-error'
import { apiMutation } from '@/lib/api/mutations'

export type Complaint = {
  id: string
  referenceNo: string
  residentId: string
  zoneId: string
  zoneName?: string | null
  zoneCode?: string | null
  title: string
  description: string
  severity: string
  status: string
  assignedToUserId: string | null
  assignedUserDisplayName?: string | null
  currentHandlerLevel: string
  createdAt: string
  updatedAt: string
  resolvedAt: string | null
}

export type ComplaintEvent = {
  id: string
  complaintId: string
  actorUserId: string | null
  action: string
  fromLevel: string | null
  toLevel: string | null
  note: string | null
  occurredAt: string
}

const root = '/smartcity360/api/v1/complaints'
const page = (offset: number, limit: number) => new URLSearchParams({ offset: String(offset), limit: String(limit) })

function isOptionalDisplayText(value: unknown): value is string | null | undefined {
  return value === undefined || value === null || typeof value === 'string'
}

function isComplaintDetail(value: unknown): value is Complaint {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const complaint = value as Record<string, unknown>
  const requiredStrings = ['id', 'referenceNo', 'residentId', 'zoneId', 'title', 'description', 'severity', 'status', 'currentHandlerLevel', 'createdAt', 'updatedAt']
  if (requiredStrings.some((field) => typeof complaint[field] !== 'string')) return false
  if (!Object.hasOwn(complaint, 'assignedToUserId')) return false
  if (complaint.assignedToUserId !== null && typeof complaint.assignedToUserId !== 'string') return false
  if (!isOptionalDisplayText(complaint.zoneName)) return false
  if (!isOptionalDisplayText(complaint.zoneCode)) return false
  if (!isOptionalDisplayText(complaint.assignedUserDisplayName)) return false
  return complaint.resolvedAt === null || typeof complaint.resolvedAt === 'string'
}

function validatedComplaint(payload: unknown): Complaint {
  if (!isComplaintDetail(payload)) {
    throw new ApiError('The complaint service returned an incompatible response.', 200, 'UNEXPECTED_RESPONSE')
  }
  return payload
}

export function listMyComplaints(offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Complaint[]>(`${root}/my?${page(offset, limit)}`, { signal })
}

export function listComplaintQueue(offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Complaint[]>(`${root}/queue?${page(offset, limit)}`, { signal })
}

export function listAllComplaints(offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Complaint[]>(`${root}?${page(offset, limit)}`, { signal })
}

export function getComplaint(id: string, signal?: AbortSignal) {
  return apiRequest<unknown>(`${root}/${encodeURIComponent(id)}`, { signal }).then(validatedComplaint)
}

export function getComplaintHistory(id: string, offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<ComplaintEvent[]>(`${root}/${encodeURIComponent(id)}/history?${page(offset, limit)}`, { signal })
}

export const submitComplaint = (body: { title: string; description: string; severity: string; zoneId: string }) =>
  apiMutation<Complaint>(root, { method: 'POST', body })

export const assignComplaintToSelf = (id: string, assigneeId: string) =>
  apiMutation<Complaint>(`${root}/${encodeURIComponent(id)}/assign`, { method: 'POST', body: { assigneeId } })

export const escalateComplaint = (id: string, expectedLevel: string, reason: string) =>
  apiMutation<Complaint>(`${root}/${encodeURIComponent(id)}/escalate`, { method: 'POST', body: { expectedLevel, reason } })

export const startComplaint = (id: string) =>
  apiMutation<Complaint>(`${root}/${encodeURIComponent(id)}/start`, { method: 'POST' })

export const resolveComplaint = (id: string, note: string) =>
  apiMutation<Complaint>(`${root}/${encodeURIComponent(id)}/resolve`, { method: 'POST', body: { note } })
