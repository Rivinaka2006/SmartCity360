import { apiRequest, type ApiRequestOptions } from './client'
import { getCsrfToken } from './csrf-store'

type MutationOptions = Omit<ApiRequestOptions, 'csrfToken'>

export async function apiMutation<T>(path: string, options: MutationOptions = {}) {
  const csrfToken = await getCsrfToken()
  return apiRequest<T>(path, { ...options, csrfToken })
}
