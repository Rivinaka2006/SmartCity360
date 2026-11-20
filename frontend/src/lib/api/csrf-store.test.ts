import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken, getCsrfToken } from './csrf-store'

afterEach(() => {
  clearCsrfToken()
  vi.unstubAllGlobals()
})

describe('CSRF token store', () => {
  it('keeps a token in memory and shares one in-flight retrieval', async () => {
    let resolveResponse!: (response: Response) => void
    const pendingResponse = new Promise<Response>((resolve) => { resolveResponse = resolve })
    const fetchMock = vi.fn().mockReturnValue(pendingResponse)
    vi.stubGlobal('fetch', fetchMock)

    const first = getCsrfToken()
    const second = getCsrfToken()
    resolveResponse(new Response(JSON.stringify({ token: 'session-bound-token' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))

    await expect(Promise.all([first, second])).resolves.toEqual(['session-bound-token', 'session-bound-token'])
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('clears the in-memory token so the next request obtains a new one', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'first-token' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'second-token' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(getCsrfToken()).resolves.toBe('first-token')
    clearCsrfToken()
    await expect(getCsrfToken()).resolves.toBe('second-token')
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })
})
