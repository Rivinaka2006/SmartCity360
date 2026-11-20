import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiRequest } from '@/lib/api/client'
import { clearCsrfToken } from '@/lib/api/csrf-store'
import { AuthProvider } from './auth-context'
import { useAuth } from './use-auth'

const authenticatedUser = {
  id: '0f6ae02d-54cc-44ec-909f-5758c3b30bd9',
  displayName: 'Asha Perera',
  email: 'asha@example.test',
  roles: ['ADMIN'],
}

function AuthProbe() {
  const auth = useAuth()
  return (
    <div>
      <span data-testid="status">{auth.status}</span>
      <span>{auth.user?.displayName}</span>
      <button type="button" onClick={() => void auth.login({ email: 'asha@example.test', password: 'secret' })}>Login</button>
      <button type="button" onClick={() => void auth.logout()}>Logout</button>
      <button type="button" onClick={() => void apiRequest('/smartcity360/api/v1/protected').catch(() => undefined)}>Expire</button>
    </div>
  )
}

function renderAuth() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>{children}</AuthProvider>
    </QueryClientProvider>
  )
  return { queryClient, ...render(<AuthProbe />, { wrapper: Wrapper }) }
}

afterEach(() => {
  clearCsrfToken()
  vi.unstubAllGlobals()
})

describe('AuthProvider', () => {
  it('keeps protected consumers in loading state until session initialization resolves', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => undefined)))

    renderAuth()

    expect(screen.getByTestId('status')).toHaveTextContent('loading')
  })

  it('treats a Payara HTML 401 during initialization as an anonymous session', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>Unauthorized</html>', {
      status: 401,
      headers: { 'Content-Type': 'text/html' },
    })))

    renderAuth()

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))
  })

  it('exposes the current authenticated caller returned by Payara', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify(authenticatedUser), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })))

    renderAuth()

    await screen.findByText('Asha Perera')
    expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
  })

  it('logs in with a session-bound CSRF token and updates the current caller', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('<html>Unauthorized</html>', { status: 401, headers: { 'Content-Type': 'text/html' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(authenticatedUser), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderAuth()
    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))

    await user.click(screen.getByRole('button', { name: 'Login' }))

    await screen.findByText('Asha Perera')
    const [, loginRequest] = fetchMock.mock.calls[2] as [string, RequestInit]
    expect(loginRequest.headers).toEqual(expect.objectContaining({ 'X-CSRF-Token': 'csrf-token' }))
  })

  it('accepts an empty 204 logout and clears authenticated state', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authenticatedUser), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderAuth()
    await screen.findByText('Asha Perera')

    await user.click(screen.getByRole('button', { name: 'Logout' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/smartcity360/api/v1/auth/logout')
  })

  it('clears authenticated state when a later protected request returns 401', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify(authenticatedUser), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response('<html>Expired</html>', { status: 401, headers: { 'Content-Type': 'text/html' } }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderAuth()
    await screen.findByText('Asha Perera')

    await user.click(screen.getByRole('button', { name: 'Expire' }))

    await waitFor(() => expect(screen.getByTestId('status')).toHaveTextContent('anonymous'))
    expect(screen.queryByText('Asha Perera')).not.toBeInTheDocument()
  })
})
