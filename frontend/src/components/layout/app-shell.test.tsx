import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { PropsWithChildren } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/lib/api/contracts'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-state'
import { AppShell } from './app-shell'

const userProfile: CurrentUser = {
  id: '0f6ae02d-54cc-44ec-909f-5758c3b30bd9',
  displayName: 'Asha Perera',
  email: 'asha@example.test',
  roles: ['RESIDENT'],
}

function renderShell(auth: AuthContextValue) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: PropsWithChildren) => (
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={auth}>
        <MemoryRouter initialEntries={['/app']}>{children}</MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>
  )
  return render(<AppShell><p>Dashboard content</p></AppShell>, { wrapper: Wrapper })
}

afterEach(() => vi.restoreAllMocks())

describe('AppShell', () => {
  it('shows the signed-in user and only navigation links allowed for their roles', () => {
    renderShell({ status: 'authenticated', user: userProfile, error: null, login: vi.fn(), logout: vi.fn() })

    expect(screen.getAllByText('Asha Perera').length).toBeGreaterThan(0)
    expect(screen.getByRole('navigation', { name: 'Main navigation' })).toHaveTextContent('Complaints')
    expect(screen.queryByRole('link', { name: 'Zones' })).not.toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Energy streams' })).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByText('Dashboard content')).toBeVisible()
  })

  it('runs logout from the account menu', async () => {
    const logout = vi.fn().mockResolvedValue(undefined)
    const user = userEvent.setup()
    renderShell({ status: 'authenticated', user: userProfile, error: null, login: vi.fn(), logout })

    await user.click(screen.getByRole('button', { name: 'Account menu' }))
    await user.click(screen.getByRole('menuitem', { name: 'Sign out' }))

    await waitFor(() => expect(logout).toHaveBeenCalledOnce())
  })

  it('provides an accessible title and description for mobile navigation without dialog warnings', async () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    const user = userEvent.setup()
    renderShell({ status: 'authenticated', user: userProfile, error: null, login: vi.fn(), logout: vi.fn() })

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))

    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveAccessibleName('SmartCity360 navigation')
    expect(dialog).toHaveAccessibleDescription('Choose a workspace page.')
    expect(consoleError).not.toHaveBeenCalled()
  })

  it('closes the mobile navigation on Escape and restores trigger focus', async () => {
    const user = userEvent.setup()
    renderShell({ status: 'authenticated', user: userProfile, error: null, login: vi.fn(), logout: vi.fn() })
    const trigger = screen.getByRole('button', { name: 'Open navigation' })

    await user.click(trigger)
    expect(screen.getByRole('dialog', { name: 'SmartCity360 navigation' })).toBeVisible()

    await user.keyboard('{Escape}')

    await waitFor(() => expect(screen.queryByRole('dialog', { name: 'SmartCity360 navigation' })).not.toBeInTheDocument())
    expect(trigger).toHaveFocus()
  })

  it('closes the mobile navigation after selecting a route', async () => {
    const user = userEvent.setup()
    renderShell({ status: 'authenticated', user: userProfile, error: null, login: vi.fn(), logout: vi.fn() })

    await user.click(screen.getByRole('button', { name: 'Open navigation' }))
    await user.click(within(screen.getByRole('dialog')).getByRole('link', { name: 'Complaints' }))

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
  })
})
