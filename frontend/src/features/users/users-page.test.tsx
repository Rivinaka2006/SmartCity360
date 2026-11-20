import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-state'
import { UsersPage } from './users-page'

afterEach(() => vi.unstubAllGlobals())

const authenticatedAdmin: AuthContextValue = {
  status: 'authenticated',
  user: { id: 'current-admin', displayName: 'Current Admin', email: 'admin@test.invalid', roles: ['ADMIN'] },
  error: null,
  login: vi.fn(),
  logout: vi.fn(),
}

function managedUser(id: number, fullName = `User ${id}`) {
  return {
    id: String(id),
    fullName,
    email: `user${id}@test.invalid`,
    status: 'ACTIVE',
    roles: ['RESIDENT'],
    integrationTestAccount: true,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
  }
}

function listResponse(users: ReturnType<typeof managedUser>[]) {
  return new Response(JSON.stringify(users), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

function renderUsersPage(auth: AuthContextValue = authenticatedAdmin) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><AuthContext.Provider value={auth}><UsersPage /></AuthContext.Provider></QueryClientProvider>)
}

function userRoleControl(name: string) {
  return `Role for ${name}`
}

function stubUserListing(users: ReturnType<typeof managedUser>[]) {
  const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
    const url = new URL(String(input), window.location.origin)
    const offset = Number(url.searchParams.get('offset'))
    const limit = Number(url.searchParams.get('limit'))
    const search = url.searchParams.get('search')?.toLowerCase() ?? ''
    const matches = users.filter((user) => `${user.fullName} ${user.email}`.toLowerCase().includes(search))
    return Promise.resolve(listResponse(matches.slice(offset, offset + limit)))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

describe('UsersPage', () => {
  it('renders two users on the first server-paginated page', async () => {
    const fetchMock = stubUserListing([managedUser(1), managedUser(2), managedUser(3)])
    renderUsersPage()

    expect(await screen.findByLabelText(userRoleControl('User 1'))).toBeInTheDocument()
    expect(screen.getByLabelText(userRoleControl('User 2'))).toBeInTheDocument()
    expect(screen.queryByLabelText(userRoleControl('User 3'))).not.toBeInTheDocument()
    expect(screen.getByText('Records 1–2')).toBeInTheDocument()
    expect(fetchMock.mock.calls[0]?.[0]).toContain('offset=0&limit=3')
  })

  it('shows the next two users and returns to the preceding page', async () => {
    stubUserListing([1, 2, 3, 4, 5].map((id) => managedUser(id)))
    const user = userEvent.setup()
    renderUsersPage()

    await screen.findByLabelText(userRoleControl('User 1'))
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByLabelText(userRoleControl('User 3'))).toBeInTheDocument()
    expect(screen.getByLabelText(userRoleControl('User 4'))).toBeInTheDocument()
    expect(screen.queryByLabelText(userRoleControl('User 5'))).not.toBeInTheDocument()
    expect(screen.getByText('Records 3–4')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await screen.findByLabelText(userRoleControl('User 1'))).toBeInTheDocument()
    expect(screen.getByLabelText(userRoleControl('User 2'))).toBeInTheDocument()
    expect(screen.queryByLabelText(userRoleControl('User 3'))).not.toBeInTheDocument()
  })

  it('resets to the first page when a search is applied', async () => {
    const fetchMock = stubUserListing([1, 2, 3, 4].map((id) => managedUser(id)).concat(managedUser(5, 'Needle match')))
    const user = userEvent.setup()
    renderUsersPage()

    await screen.findByLabelText(userRoleControl('User 1'))
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByLabelText(userRoleControl('User 3'))).toBeInTheDocument()
    expect(fetchMock.mock.calls.at(-1)?.[0]).toContain('offset=2&limit=3')

    await user.type(screen.getByLabelText('Search users'), 'needle')
    await user.click(screen.getByRole('button', { name: 'Search' }))

    expect(await screen.findByLabelText(userRoleControl('Needle match'))).toBeInTheDocument()
    expect(screen.getByText('Records 1–1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(fetchMock.mock.calls.at(-1)?.[0]).toContain('search=needle&offset=0&limit=3')
  })

  it('uses the returned record count for ranges and prevents advancing past the last page', async () => {
    stubUserListing([managedUser(1), managedUser(2), managedUser(3)])
    const user = userEvent.setup()
    renderUsersPage()

    await screen.findByLabelText(userRoleControl('User 1'))
    await user.click(screen.getByRole('button', { name: 'Next' }))

    expect(await screen.findByLabelText(userRoleControl('User 3'))).toBeInTheDocument()
    expect(screen.getByText('Records 3–3')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('keeps role, status, and user creation controls available', async () => {
    stubUserListing([managedUser(1)])
    renderUsersPage()

    await screen.findByLabelText(userRoleControl('User 1'))
    expect(screen.getByLabelText('Role for User 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Status for User 1')).toBeInTheDocument()
    expect(screen.getByLabelText('Full name')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toBeInTheDocument()
    expect(screen.getByLabelText('Initial password')).toBeInTheDocument()
    expect(screen.getByLabelText('Initial role')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Create user' })).toBeInTheDocument()
  })

  it('disables role and status controls for the authenticated administrator’s own ID', async () => {
    const self = { ...managedUser(7, 'Different profile name'), email: 'different@test.invalid', roles: ['RESIDENT'] }
    stubUserListing([self])
    renderUsersPage({
      ...authenticatedAdmin,
      user: { id: '7', displayName: 'Current Admin', email: 'admin@test.invalid', roles: ['ADMIN'] },
    })

    const role = await screen.findByLabelText('Role for Different profile name')
    const status = screen.getByLabelText('Status for Different profile name')
    const explanation = screen.getByText('You cannot change your own administrator role or account status.')

    expect(role).toBeDisabled()
    expect(status).toBeDisabled()
    expect(explanation).toHaveAttribute('role', 'note')
    expect(role).toHaveAttribute('aria-describedby', explanation.id)
    expect(status).toHaveAttribute('aria-describedby', explanation.id)
    expect(screen.getByText('different@test.invalid')).toBeInTheDocument()
    expect(screen.getByText('RESIDENT', { selector: '.role-badge' })).toBeInTheDocument()
    expect(screen.getByText('ACTIVE', { selector: '.status-pill' })).toBeInTheDocument()
  })

  it('uses stable IDs rather than names or ADMIN roles to identify the current account', async () => {
    const sameNameAndRole = { ...managedUser(8, 'Current Admin'), roles: ['ADMIN'] }
    stubUserListing([sameNameAndRole])
    renderUsersPage()

    expect(await screen.findByLabelText('Role for Current Admin')).toBeEnabled()
    expect(screen.getByLabelText('Status for Current Admin')).toBeEnabled()
    expect(screen.queryByText('You cannot change your own administrator role or account status.')).not.toBeInTheDocument()
  })

  it('keeps the self-management guard applied after searches and pagination', async () => {
    const users = [managedUser(1), managedUser(2), managedUser(3, 'Search match'), managedUser(4)]
    const user = userEvent.setup()
    stubUserListing(users)
    renderUsersPage({
      ...authenticatedAdmin,
      user: { id: '3', displayName: 'Current Admin', email: 'admin@test.invalid', roles: ['ADMIN'] },
    })

    await screen.findByLabelText(userRoleControl('User 1'))
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByLabelText('Role for Search match')).toBeDisabled()
    expect(screen.getByLabelText('Status for Search match')).toBeDisabled()

    await user.type(screen.getByLabelText('Search users'), 'search match')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByLabelText('Role for Search match')).toBeDisabled()
    expect(screen.getByLabelText('Status for Search match')).toBeDisabled()
    expect(screen.getByText('You cannot change your own administrator role or account status.')).toBeInTheDocument()
  })

  it('does not attempt role or status mutations through disabled self-management controls', async () => {
    const fetchMock = stubUserListing([managedUser(9, 'My account')])
    const confirm = vi.fn()
    vi.stubGlobal('confirm', confirm)
    const user = userEvent.setup()
    renderUsersPage({
      ...authenticatedAdmin,
      user: { id: '9', displayName: 'Current Admin', email: 'admin@test.invalid', roles: ['ADMIN'] },
    })

    const role = await screen.findByLabelText('Role for My account')
    const status = screen.getByLabelText('Status for My account')
    await user.selectOptions(role, 'ADMIN')
    await user.selectOptions(status, 'SUSPENDED')

    expect(confirm).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('keeps role and status changes available with confirmation for other accounts', async () => {
    const fetchMock = stubUserListing([managedUser(10, 'Other Admin')])
    const confirm = vi.fn().mockReturnValue(false)
    vi.stubGlobal('confirm', confirm)
    const user = userEvent.setup()
    renderUsersPage()

    const role = await screen.findByLabelText('Role for Other Admin')
    const status = screen.getByLabelText('Status for Other Admin')
    expect(role).toBeEnabled()
    expect(status).toBeEnabled()

    await user.selectOptions(role, 'ADMIN')
    await user.selectOptions(status, 'SUSPENDED')

    expect(confirm).toHaveBeenNthCalledWith(1, "Replace Other Admin's current roles with ADMIN?")
    expect(confirm).toHaveBeenNthCalledWith(2, "Change Other Admin's status to SUSPENDED?")
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('keeps management controls disabled until authenticated identity is available', async () => {
    stubUserListing([managedUser(11)])
    renderUsersPage({ ...authenticatedAdmin, status: 'loading', user: null })

    expect(await screen.findByLabelText(userRoleControl('User 11'))).toBeDisabled()
    expect(screen.getByLabelText('Status for User 11')).toBeDisabled()
    expect(screen.getByText('User role and status controls are unavailable until your account identity is verified.')).toBeInTheDocument()
  })

  it('keeps empty and single-result states usable', async () => {
    stubUserListing([])
    const emptyResult = renderUsersPage()
    expect(await screen.findByText('No users found')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Next' })).not.toBeInTheDocument()
    emptyResult.unmount()

    stubUserListing([managedUser(1)])
    renderUsersPage()
    expect(await screen.findByLabelText(userRoleControl('User 1'))).toBeInTheDocument()
    expect(screen.getByText('Records 1–1')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
  })

  it('collects an administrator-entered password without retaining it after creation', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'user-1' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()
    render(<QueryClientProvider client={queryClient}><AuthContext.Provider value={authenticatedAdmin}><UsersPage /></AuthContext.Provider></QueryClientProvider>)

    const password = await screen.findByLabelText('Initial password')
    await user.type(screen.getByLabelText('Full name'), 'Test Resident')
    await user.type(screen.getByLabelText('Email'), 'resident@test.invalid')
    await user.type(password, 'A sufficiently long password')
    await user.click(screen.getByRole('button', { name: 'Create user' }))

    expect(await screen.findByRole('status')).toHaveTextContent('User account created.')
    expect(password).toHaveValue('')
    expect(fetchMock.mock.calls[2]?.[1]).toEqual(expect.objectContaining({
      body: expect.stringContaining('"password":"A sufficiently long password"'),
    }))
  })
})
