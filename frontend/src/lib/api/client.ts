import { ApiError, type ApiErrorCode } from './api-error'
import type { BackendError } from './contracts'

export type ApiRequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  body?: unknown
  csrfToken?: string
  signal?: AbortSignal
  acceptedStatuses?: readonly number[]
}

let unauthorizedHandler: (() => void) | undefined

export function setUnauthorizedHandler(handler: (() => void) | undefined) {
  unauthorizedHandler = handler
}

const statusDetails: Record<number, { code: ApiErrorCode; message: string }> = {
  400: { code: 'INVALID_REQUEST', message: 'The request could not be completed.' },
  401: { code: 'AUTHENTICATION_REQUIRED', message: 'Authentication is required.' },
  403: { code: 'PERMISSION_DENIED', message: 'You do not have permission to perform this action.' },
  404: { code: 'NOT_FOUND', message: 'The requested resource was not found.' },
  409: { code: 'CONFLICT', message: 'The request conflicts with the current state.' },
  429: { code: 'RATE_LIMITED', message: 'Too many requests. Please try again later.' },
}

function isJson(response: Response) {
  return response.headers.get('Content-Type')?.toLowerCase().includes('application/json') ?? false
}

async function parseJsonSafely(response: Response): Promise<unknown> {
  if (!isJson(response)) return undefined
  try {
    return await response.json()
  } catch {
    return undefined
  }
}

function isBackendError(value: unknown): value is BackendError {
  return typeof value === 'object' && value !== null
}

function safeBackendMessage(value: unknown, fallback: string) {
  if (!isBackendError(value) || typeof value.error !== 'string') return fallback
  const message = value.error.trim()
  if (!message || message.length > 160 || /[<>\r\n]/.test(message)) return fallback
  return message
}

function errorFor(response: Response, payload: unknown) {
  const details = statusDetails[response.status] ?? (
    response.status >= 500
      ? { code: 'SERVER_ERROR' as const, message: 'The service is temporarily unavailable.' }
      : { code: 'UNEXPECTED_RESPONSE' as const, message: 'The request could not be completed.' }
  )
  const message = response.status >= 500 ? details.message : safeBackendMessage(payload, details.message)
  const backendCode = isBackendError(payload) && typeof payload.code === 'string' ? payload.code : undefined
  return new ApiError(message, response.status, details.code, backendCode)
}

export async function apiRequest<T = void>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.csrfToken) headers['X-CSRF-Token'] = options.csrfToken

  let response: Response
  try {
    response = await fetch(path, {
      method: options.method ?? 'GET',
      credentials: 'same-origin',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new ApiError('The backend could not be reached.', 0, 'NETWORK_ERROR')
  }

  if (response.status === 204) return undefined as T
  const payload = await parseJsonSafely(response)
  if (!response.ok && !options.acceptedStatuses?.includes(response.status)) {
    if (response.status === 401) unauthorizedHandler?.()
    throw errorFor(response, payload)
  }
  if (payload === undefined) {
    throw new ApiError('The service returned an unexpected response.', response.status, 'UNEXPECTED_RESPONSE')
  }
  return payload as T
}
