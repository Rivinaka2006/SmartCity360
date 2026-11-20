import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '@/lib/api/csrf-store'
import { createManagedUser, listManagedUsers, updateManagedUserStatus, USERS_PER_PAGE } from './user-api'

afterEach(() => {
  clearCsrfToken()
  vi.unstubAllGlobals()
})

describe('user management API contracts', () => {
  it('uses paginated, searchable user listing parameters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await listManagedUsers('test user', USERS_PER_PAGE, USERS_PER_PAGE)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/smartcity360/api/v1/users?search=test+user&offset=2&limit=2')
  })

  it('submits create and status payloads through the CSRF mutation flow', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'user-1' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await createManagedUser({ fullName: '[TEST] Resident', email: 'resident@test.invalid', password: 'A long test password', role: 'RESIDENT', integrationTestAccount: true })

    expect(fetchMock.mock.calls[1]).toEqual([
      '/smartcity360/api/v1/users',
      expect.objectContaining({ method: 'POST', body: expect.stringContaining('"role":"RESIDENT"') }),
    ])

    fetchMock.mockReset()
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'user-1' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    clearCsrfToken()

    await updateManagedUserStatus('user-1', 'DISABLED')

    expect(fetchMock.mock.calls[1]).toEqual([
      '/smartcity360/api/v1/users/user-1/status',
      expect.objectContaining({ method: 'PUT', body: '{"status":"DISABLED"}' }),
    ])
  })
})
