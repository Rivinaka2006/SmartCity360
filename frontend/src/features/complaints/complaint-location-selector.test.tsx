import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '@/features/auth/auth-state'
import { ComplaintsPage } from './complaints-page'

const location = { id: 'zone-1', name: 'Colombo', code: '001', type: 'CITY' }

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

afterEach(() => vi.unstubAllGlobals())

describe('complaint location selector', () => {
  it('shows active location labels and submits its internal zoneId', async () => {
    const fetchMock = vi.fn((url: string, init?: RequestInit) => {
      if (url.includes('/complaint-locations?')) return Promise.resolve(response([location]))
      if (url.includes('/complaints/my?')) return Promise.resolve(response([]))
      if (url.endsWith('/auth/csrf')) return Promise.resolve(response({ token: 'csrf-token' }))
      if (url.endsWith('/complaints') && init?.method === 'POST') return Promise.resolve(response({ id: 'complaint-1', referenceNo: 'SC-001' }, 201))
      return Promise.resolve(response([]))
    })
    vi.stubGlobal('fetch', fetchMock)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()
    render(<QueryClientProvider client={queryClient}><AuthContext.Provider value={{ status: 'authenticated', user: { id: 'resident-1', displayName: 'Resident', email: 'resident@test.invalid', roles: ['RESIDENT'] }, error: null, login: vi.fn(), logout: vi.fn() }}><ComplaintsPage /></AuthContext.Provider></QueryClientProvider>)

    await user.selectOptions(await screen.findByLabelText('Complaint location'), 'zone-1')
    await user.type(screen.getByLabelText('Title'), 'Low severity test')
    await user.type(screen.getByLabelText('Description'), 'Controlled complaint workflow verification.')
    await user.click(screen.getByRole('button', { name: 'Submit complaint' }))

    expect(screen.getByRole('option', { name: 'Colombo (CITY · 001)' })).toBeVisible()
    const [, request] = fetchMock.mock.calls.find(([url, init]) => url.endsWith('/complaints') && init?.method === 'POST') as [string, RequestInit]
    expect(request.body).toContain('"zoneId":"zone-1"')
  })
})
