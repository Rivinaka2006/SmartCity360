import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '@/lib/api/csrf-store'
import { AuthProvider } from './auth-context'
import { LoginPage } from './login-page'

function renderLogin() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider><LoginPage /></AuthProvider>
    </QueryClientProvider>,
  )
}

afterEach(() => {
  clearCsrfToken()
  vi.unstubAllGlobals()
})

describe('LoginPage', () => {
  it('labels fields and rejects malformed credentials before sending login', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('<html>Unauthorized</html>', {
      status: 401,
      headers: { 'Content-Type': 'text/html' },
    }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderLogin()
    await screen.findByRole('button', { name: 'Sign in' })

    await user.type(screen.getByLabelText('Email address'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter a valid email address.')).toBeVisible()
    expect(screen.getByText('Password is required.')).toBeVisible()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('shows a generic message for a rejected login without revealing account existence', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('<html>Unauthorized</html>', { status: 401, headers: { 'Content-Type': 'text/html' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ error: 'Invalid credentials' }), { status: 401, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderLogin()
    await screen.findByRole('button', { name: 'Sign in' })

    await user.type(screen.getByLabelText('Email address'), 'unknown@example.test')
    await user.type(screen.getByLabelText('Password'), 'not-the-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Email or password is incorrect.'))
    expect(screen.getByRole('alert')).not.toHaveTextContent('unknown')
  })
})
