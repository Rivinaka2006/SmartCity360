import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from './api-error'
import { apiRequest } from './client'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiRequest', () => {
  it('returns a JSON response and always uses same-origin session credentials', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP', service: 'smartcity360' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(apiRequest('/smartcity360/api/v1/health')).resolves.toEqual({ status: 'UP', service: 'smartcity360' })
    expect(fetchMock).toHaveBeenCalledWith('/smartcity360/api/v1/health', expect.objectContaining({
      credentials: 'same-origin',
      headers: expect.objectContaining({ Accept: 'application/json' }),
    }))
  })

  it('sends the exact backend login DTO without invented fields', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: '0f6ae02d-54cc-44ec-909f-5758c3b30bd9',
      displayName: 'Asha Perera',
      email: 'asha@example.test',
      roles: ['ADMIN'],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await apiRequest('/smartcity360/api/v1/auth/login', {
      method: 'POST',
      body: { email: 'asha@example.test', password: 'correct horse battery staple' },
      csrfToken: 'csrf-value',
    })

    const [, request] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(request.body).toBe('{"email":"asha@example.test","password":"correct horse battery staple"}')
    expect(request.headers).toEqual(expect.objectContaining({
      'Content-Type': 'application/json',
      'X-CSRF-Token': 'csrf-value',
    }))
  })

  it('normalizes a Payara HTML 401 without exposing HTML to the caller', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html><body>Payara stack details</body></html>', {
      status: 401,
      headers: { 'Content-Type': 'text/html' },
    })))

    const error = await apiRequest('/smartcity360/api/v1/auth/me').catch((caught: unknown) => caught)

    expect(error).toBeInstanceOf(ApiError)
    expect(error).toMatchObject({ status: 401, code: 'AUTHENTICATION_REQUIRED' })
    expect((error as Error).message).toBe('Authentication is required.')
    expect((error as Error).message).not.toContain('html')
    expect((error as Error).message).not.toContain('Payara')
  })

  it('returns undefined for the backend empty 204 logout response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))

    await expect(apiRequest('/smartcity360/api/v1/auth/logout', {
      method: 'POST',
      csrfToken: 'csrf-value',
    })).resolves.toBeUndefined()
  })

  it.each([
    [400, 'INVALID_REQUEST'],
    [403, 'PERMISSION_DENIED'],
    [404, 'NOT_FOUND'],
    [409, 'CONFLICT'],
    [429, 'RATE_LIMITED'],
    [500, 'SERVER_ERROR'],
  ] as const)('normalizes HTTP %i as %s', async (status, code) => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('not-json', {
      status,
      headers: { 'Content-Type': 'text/plain' },
    })))

    await expect(apiRequest('/smartcity360/api/v1/test')).rejects.toMatchObject({ status, code })
  })

  it('forwards request cancellation without converting it into a server error', async () => {
    const controller = new AbortController()
    const abortError = new DOMException('The request was cancelled', 'AbortError')
    const fetchMock = vi.fn().mockRejectedValue(abortError)
    vi.stubGlobal('fetch', fetchMock)

    const promise = apiRequest('/smartcity360/api/v1/health', { signal: controller.signal })
    controller.abort()

    await expect(promise).rejects.toBe(abortError)
    expect(fetchMock).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ signal: controller.signal }))
  })
})
