import { apiRequest } from '@/lib/api/client'
import { apiMutation } from '@/lib/api/mutations'

export type Policy = {
  id: string
  policyCode: string
  name: string
  domain: string
  description: string
  status: string
  createdBy: string
  createdAt: string
  updatedAt: string
}

export type PolicyVersion = {
  id: string
  policyId: string
  versionNo: number
  dslVersion: string
  dslExpression: string
  valid: boolean
  validationError: string | null
  active: boolean
  createdBy: string
  createdAt: string
}

export type PolicyEvaluation = {
  id: string
  policyId: string
  policyVersionId: string
  versionNo: number
  evaluatedBy: string | null
  status: 'TRUE' | 'FALSE' | 'ERROR' | string
  result: boolean | null
  errorCode: string | null
  durationMs: number
  evaluatedAt: string
  explanationJson: string | null
}

const root = '/smartcity360/api/v1/policies'
const page = (offset: number, limit: number) => new URLSearchParams({ offset: String(offset), limit: String(limit) })

export function listPolicies(offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Policy[]>(`${root}?${page(offset, limit)}`, { signal })
}

export function getPolicy(id: string, signal?: AbortSignal) {
  return apiRequest<Policy>(`${root}/${encodeURIComponent(id)}`, { signal })
}

export function getPolicyVersions(id: string, offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<PolicyVersion[]>(`${root}/${encodeURIComponent(id)}/versions?${page(offset, limit)}`, { signal })
}

export function getPolicyEvaluations(id: string, offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<PolicyEvaluation[]>(`${root}/${encodeURIComponent(id)}/evaluations?${page(offset, limit)}`, { signal })
}

export const createPolicy = (body: { policyCode: string; name: string; domain: string; description: string }) =>
  apiMutation<Policy>(root, { method: 'POST', body })

export const createPolicyVersion = (id: string, dslExpression: string) =>
  apiMutation<PolicyVersion>(`${root}/${encodeURIComponent(id)}/versions`, { method: 'POST', body: { dslExpression } })

export const validatePolicyVersion = (id: string, versionId: string) =>
  apiMutation<PolicyVersion>(`${root}/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/validate`, { method: 'POST' })

export const activatePolicyVersion = (id: string, versionId: string) =>
  apiMutation<PolicyVersion>(`${root}/${encodeURIComponent(id)}/versions/${encodeURIComponent(versionId)}/activate`, { method: 'POST' })

export const deactivatePolicy = (id: string) =>
  apiMutation<Policy>(`${root}/${encodeURIComponent(id)}/deactivate`, { method: 'POST' })

export const evaluatePolicy = (id: string, context: Record<string, unknown>) =>
  apiMutation<PolicyEvaluation>(`${root}/${encodeURIComponent(id)}/evaluate`, {
    method: 'POST',
    body: { context },
    acceptedStatuses: [400],
  })
