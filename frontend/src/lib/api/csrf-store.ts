import { ApiError } from './api-error'
import { apiRequest } from './client'
import type { CsrfResponse } from './contracts'
import { apiPaths } from './paths'

let token: string | undefined
let pending: Promise<string> | undefined

export function clearCsrfToken() {
  token = undefined
  pending = undefined
}

export function getCsrfToken(): Promise<string> {
  if (token) return Promise.resolve(token)
  if (pending) return pending

  pending = apiRequest<CsrfResponse>(apiPaths.auth.csrf)
    .then((response) => {
      if (typeof response.token !== 'string' || !response.token) {
        throw new ApiError('The security token response was invalid.', 0, 'UNEXPECTED_RESPONSE')
      }
      token = response.token
      return response.token
    })
    .finally(() => {
      pending = undefined
    })
  return pending
}
