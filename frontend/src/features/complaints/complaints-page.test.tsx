import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/lib/api/contracts'
import { AuthContext } from '@/features/auth/auth-state'
import { ComplaintsPage } from './complaints-page'
import type { Complaint } from './complaint-api'

const complaint: Complaint = {
  id: 'complaint-1',
  referenceNo: 'SC-001',
  residentId: 'resident-1',
  zoneId: 'zone-1',
  zoneName: 'Gannoruwa',
  zoneCode: 'S001',
  title: 'Streetlight outage',
  description: 'The streetlight is not operating.',
  severity: 'MEDIUM',
  status: 'OPEN',
  assignedToUserId: 'staff-1',
  assignedUserDisplayName: 'Local Staff User',
  currentHandlerLevel: 'LOCAL_STAFF',
  createdAt: '2026-11-07T00:00:00Z',
  updatedAt: '2026-11-07T00:00:00Z',
  resolvedAt: null,
}

function renderComplaints(user: CurrentUser) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={{ status: 'authenticated', user, error: null, login: vi.fn(), logout: vi.fn() }}>
        <ComplaintsPage />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

function stubComplaintRequests(complaintData: Complaint = complaint) {
  const fetchMock = vi.fn((request: string) => {
    if (request.includes('/history?')) {
      return Promise.resolve(jsonResponse([]))
    }
    if (request.endsWith('/complaint-1')) {
      return Promise.resolve(jsonResponse(complaintData))
    }
    return Promise.resolve(jsonResponse([complaintData]))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function managedUser(id: string, fullName: string, email: string, roles: string[], status = 'ACTIVE') {
  return {
    id,
    fullName,
    email,
    status,
    roles,
    integrationTestAccount: false,
    createdAt: '2026-11-07T00:00:00Z',
    updatedAt: '2026-11-07T00:00:00Z',
  }
}

function stubAdminAssignmentRequests(
  usersForPage: (search: string, offset: number) => unknown[] = () => [
    managedUser('staff-eligible', 'Casey Local', 'casey@example.test', ['LOCAL_STAFF']),
    managedUser('staff-inactive', 'Inactive Local', 'inactive@example.test', ['LOCAL_STAFF'], 'DISABLED'),
    managedUser('staff-wrong-role', 'Wrong Role', 'wrong@example.test', ['SUPERVISOR']),
  ],
  assignmentStatus = 200,
) {
  const unassignedComplaint = { ...complaint, assignedToUserId: null, assignedUserDisplayName: null }
  let assignedAssigneeId: string | null = null
  const fetchMock = vi.fn((request: string, init?: RequestInit) => {
    const url = new URL(request, 'http://localhost')
    if (url.pathname.endsWith('/history')) return Promise.resolve(jsonResponse([]))
    if (url.pathname.endsWith('/auth/csrf')) return Promise.resolve(jsonResponse({ token: 'csrf-token' }))
    if (url.pathname.endsWith('/assign') && init?.method === 'POST') {
      if (assignmentStatus !== 200) return Promise.resolve(jsonResponse({ error: 'Assignment rejected by the server.' }, assignmentStatus))
      const body = JSON.parse(String(init.body)) as { assigneeId: string }
      assignedAssigneeId = body.assigneeId
      return Promise.resolve(jsonResponse({ ...unassignedComplaint, assignedToUserId: body.assigneeId }))
    }
    if (url.pathname.endsWith('/users')) {
      return Promise.resolve(jsonResponse(usersForPage(url.searchParams.get('search') ?? '', Number(url.searchParams.get('offset')))))
    }
    if (url.pathname.endsWith('/complaint-1')) return Promise.resolve(jsonResponse({
      ...unassignedComplaint,
      assignedToUserId: assignedAssigneeId,
      assignedUserDisplayName: assignedAssigneeId ? 'Casey Local' : null,
    }))
    if (url.pathname.endsWith('/complaints')) return Promise.resolve(jsonResponse([unassignedComplaint]))
    return Promise.resolve(jsonResponse([]))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('ComplaintsPage workflow actions', () => {
  it('displays authorized zone, assignee, and handler labels without lookup requests', async () => {
    stubComplaintRequests()
    const user = userEvent.setup()
    renderComplaints({
      id: 'staff-1',
      displayName: 'Local staff',
      email: 'staff@example.test',
      roles: ['LOCAL_STAFF'],
    })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByText('Gannoruwa (S001)')).toBeVisible()
    expect(screen.getByText('Local Staff User')).toBeVisible()
    expect(screen.getByText('Local Staff')).toBeVisible()
  })

  it('uses safe labels when optional display values are unavailable', async () => {
    stubComplaintRequests({
      ...complaint,
      zoneName: undefined,
      zoneCode: undefined,
      assignedToUserId: 'staff-1',
      assignedUserDisplayName: undefined,
    })
    const user = userEvent.setup()
    renderComplaints({
      id: 'admin-1',
      displayName: 'Admin',
      email: 'admin@example.test',
      roles: ['ADMIN'],
    })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByText('Zone unavailable')).toBeVisible()
    expect(screen.getByText('Assigned user unavailable')).toBeVisible()
    expect(screen.queryByText('zone-1')).not.toBeInTheDocument()
    expect(screen.queryByText('staff-1')).not.toBeInTheDocument()
  })

  it('displays Unassigned when the authorized detail explicitly has no assignee', async () => {
    stubComplaintRequests({ ...complaint, assignedToUserId: null, assignedUserDisplayName: null })
    const user = userEvent.setup()
    renderComplaints({
      id: 'staff-1',
      displayName: 'Local staff',
      email: 'staff@example.test',
      roles: ['LOCAL_STAFF'],
    })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByText('Unassigned')).toBeVisible()
  })

  it('shows ADMIN workflow actions for a complaint assigned to another user', async () => {
    stubComplaintRequests()
    const user = userEvent.setup()
    renderComplaints({
      id: 'admin-1',
      displayName: 'Admin',
      email: 'admin@example.test',
      roles: ['ADMIN'],
    })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByRole('button', { name: 'Start work' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Escalate to next level' })).toBeVisible()
  })

  it('does not show workflow actions to a RESIDENT', async () => {
    stubComplaintRequests()
    const user = userEvent.setup()
    renderComplaints({
      id: 'resident-1',
      displayName: 'Resident',
      email: 'resident@example.test',
      roles: ['RESIDENT'],
    })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    await screen.findByText('Streetlight outage')
    expect(screen.queryByRole('button', { name: 'Start work' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Escalate to next level' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Resolve' })).not.toBeInTheDocument()
  })

  it('shows Assign to me to LOCAL_STAFF for an OPEN unassigned LOCAL_STAFF complaint', async () => {
    const fetchMock = stubComplaintRequests({ ...complaint, assignedToUserId: null })
    const user = userEvent.setup()
    renderComplaints({
      id: 'staff-1',
      displayName: 'Local staff',
      email: 'staff@example.test',
      roles: ['LOCAL_STAFF'],
    })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByRole('button', { name: 'Assign to me' })).toBeVisible()
    expect(fetchMock.mock.calls.some(([request]) => request.includes('/users?'))).toBe(false)
  })

  it('lets ADMIN select eligible staff by name and email and submits only the selected UUID', async () => {
    const fetchMock = stubAdminAssignmentRequests()
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByLabelText('Search staff by name or email')).toBeVisible()
    expect(screen.queryByLabelText('Assignee user ID')).not.toBeInTheDocument()
    const selector = await screen.findByLabelText('Eligible staff member')
    expect(await screen.findByRole('option', { name: 'Casey Local (casey@example.test)' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Inactive Local/ })).not.toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Wrong Role/ })).not.toBeInTheDocument()

    await user.selectOptions(selector, 'staff-eligible')
    await user.click(screen.getByRole('button', { name: 'Assign complaint' }))

    await waitFor(() => {
      const call = fetchMock.mock.calls.find(([request, init]) => request.endsWith('/assign') && init?.method === 'POST')
      expect(call?.[1]?.body).toBe('{"assigneeId":"staff-eligible"}')
      expect(call?.[1]?.headers).toEqual(expect.objectContaining({ 'X-CSRF-Token': 'csrf-token' }))
    })
    expect(await screen.findByText('Casey Local')).toBeVisible()
    expect(screen.queryByLabelText('Eligible staff member')).not.toBeInTheDocument()
  })

  it('paginates through ineligible users to discover eligible staff on a later page', async () => {
    const secondPageUser = managedUser('staff-page-two', 'Jordan Staff', 'jordan@example.test', ['LOCAL_STAFF'])
    const fetchMock = stubAdminAssignmentRequests((_search, offset) => offset === 0
      ? Array.from({ length: 25 }, (_, index) => managedUser(`resident-${index}`, `Resident ${index}`, `resident${index}@example.test`, ['RESIDENT']))
      : [secondPageUser])
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByText('No eligible staff on this page. More users may be available.')).toBeVisible()
    await user.click(screen.getByRole('button', { name: 'More users' }))

    expect(await screen.findByRole('option', { name: 'Jordan Staff (jordan@example.test)' })).toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([request]) => request.includes('offset=25&limit=25'))).toBe(true)
  })

  it('resets pagination and clears the selected staff member when search changes', async () => {
    const fetchMock = stubAdminAssignmentRequests((search, offset) => search
      ? [managedUser('staff-search-result', 'Casey Local', 'casey@example.test', ['LOCAL_STAFF'])]
      : offset === 0
        ? Array.from({ length: 25 }, (_, index) => managedUser(`staff-${index}`, `Staff ${index}`, `staff${index}@example.test`, ['LOCAL_STAFF']))
        : [managedUser('staff-later', 'Later Staff', 'later@example.test', ['LOCAL_STAFF'])])
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))
    await screen.findByRole('button', { name: 'More users' })
    await user.click(screen.getByRole('button', { name: 'More users' }))
    const selector = await screen.findByLabelText('Eligible staff member')
    await user.selectOptions(selector, 'staff-later')
    expect(selector).toHaveValue('staff-later')

    const search = screen.getByLabelText('Search staff by name or email')
    await user.type(search, 'Jordan')

    expect(await screen.findByRole('option', { name: 'Casey Local (casey@example.test)' })).toBeInTheDocument()
    expect(screen.getByLabelText('Eligible staff member')).toHaveValue('')
    expect(fetchMock.mock.calls.some(([request]) => request.includes('search=Jordan&offset=0&limit=25'))).toBe(true)
  })

  it('reports an exhausted search accurately when no users match', async () => {
    stubAdminAssignmentRequests(() => [])
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByText('No users match this search.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Assign complaint' })).toBeDisabled()
  })

  it('announces candidate loading and reports when the current page is empty', async () => {
    let resolveUsers: (response: Response) => void = () => {}
    const pendingUsers = new Promise<Response>((resolve) => { resolveUsers = resolve })
    const fetchMock = vi.fn((request: string) => {
      const url = new URL(request, 'http://localhost')
      if (url.pathname.endsWith('/history')) return Promise.resolve(jsonResponse([]))
      if (url.pathname.endsWith('/users')) return pendingUsers
      if (url.pathname.endsWith('/complaint-1')) return Promise.resolve(jsonResponse({ ...complaint, assignedToUserId: null }))
      return Promise.resolve(jsonResponse([{ ...complaint, assignedToUserId: null }]))
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))
    expect(await screen.findByRole('status')).toHaveTextContent('Loading staff candidates')
    resolveUsers(jsonResponse([]))
    expect(await screen.findByText('No users match this search.')).toBeVisible()
  })

  it('lets ADMIN return from an empty page at the end of the user results', async () => {
    const fetchMock = stubAdminAssignmentRequests((_search, offset) => offset === 0
      ? Array.from({ length: 25 }, (_, index) => managedUser(`resident-${index}`, `Resident ${index}`, `resident${index}@example.test`, ['RESIDENT']))
      : [])
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))
    await screen.findByText('No eligible staff on this page. More users may be available.')
    await user.click(await screen.findByRole('button', { name: 'More users' }))

    expect(await screen.findByText('No more users are available for this search.')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Previous users' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'More users' })).toBeDisabled()
    expect(fetchMock.mock.calls.some(([request]) => request.includes('offset=25&limit=25'))).toBe(true)
  })

  it('shows user-list request errors with a retry action', async () => {
    const fetchMock = vi.fn((request: string) => {
      const url = new URL(request, 'http://localhost')
      if (url.pathname.endsWith('/history')) return Promise.resolve(jsonResponse([]))
      if (url.pathname.endsWith('/users')) return Promise.resolve(jsonResponse({ error: 'Directory unavailable.' }, 503))
      if (url.pathname.endsWith('/complaint-1')) return Promise.resolve(jsonResponse({ ...complaint, assignedToUserId: null }))
      return Promise.resolve(jsonResponse([{ ...complaint, assignedToUserId: null }]))
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The service is temporarily unavailable.')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('shows the assignment API rejection without hiding the server message', async () => {
    stubAdminAssignmentRequests(undefined, 409)
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))
    await user.selectOptions(await screen.findByLabelText('Eligible staff member'), 'staff-eligible')
    await user.click(screen.getByRole('button', { name: 'Assign complaint' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Assignment rejected by the server.')
  })

  it('clears the selection when a different complaint and handler level are selected', async () => {
    const localComplaint = { ...complaint, assignedToUserId: null, assignedUserDisplayName: null }
    const supervisorComplaint = {
      ...localComplaint,
      id: 'complaint-2',
      referenceNo: 'SC-002',
      currentHandlerLevel: 'SUPERVISOR',
      title: 'Water supply issue',
    }
    const fetchMock = vi.fn((request: string) => {
      const url = new URL(request, 'http://localhost')
      if (url.pathname.endsWith('/history')) return Promise.resolve(jsonResponse([]))
      if (url.pathname.endsWith('/users')) return Promise.resolve(jsonResponse([
        managedUser('local-candidate', 'Local Candidate', 'local@example.test', ['LOCAL_STAFF']),
        managedUser('supervisor-candidate', 'Supervisor Candidate', 'supervisor@example.test', ['SUPERVISOR']),
      ]))
      if (url.pathname.endsWith('/complaint-1')) return Promise.resolve(jsonResponse(localComplaint))
      if (url.pathname.endsWith('/complaint-2')) return Promise.resolve(jsonResponse(supervisorComplaint))
      return Promise.resolve(jsonResponse([localComplaint, supervisorComplaint]))
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderComplaints({ id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))
    let selector = await screen.findByLabelText('Eligible staff member')
    await user.selectOptions(selector, 'local-candidate')
    expect(selector).toHaveValue('local-candidate')

    await user.click(screen.getByRole('button', { name: /SC-002/i }))

    selector = await screen.findByLabelText('Eligible staff member')
    expect(selector).toHaveValue('')
    expect(screen.getByRole('option', { name: 'Supervisor Candidate (supervisor@example.test)' })).toBeInTheDocument()
    expect(screen.queryByRole('option', { name: /Local Candidate/ })).not.toBeInTheDocument()
  })

  it('clears stale details and refreshes the queue after escalation succeeds', async () => {
    const fetchMock = vi.fn((request: string, init?: RequestInit) => {
      if (request.includes('/history?')) return Promise.resolve(jsonResponse([]))
      if (request.endsWith('/auth/csrf')) return Promise.resolve(jsonResponse({ token: 'csrf-token' }))
      if (request.endsWith('/complaint-1/escalate') && init?.method === 'POST') return Promise.resolve(jsonResponse({ ...complaint, assignedToUserId: null, currentHandlerLevel: 'SUPERVISOR', status: 'ESCALATED' }))
      if (request.endsWith('/complaint-1')) return Promise.resolve(jsonResponse(complaint))
      return Promise.resolve(jsonResponse([complaint]))
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderComplaints({ id: 'staff-1', displayName: 'Local staff', email: 'staff@example.test', roles: ['LOCAL_STAFF'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))
    await user.type(await screen.findByLabelText('Reason'), 'Requires supervisory review')
    await user.click(screen.getByRole('button', { name: 'Escalate to next level' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Complaint SC-001 escalated successfully.')
    expect(screen.getByText('Select a complaint')).toBeVisible()
    expect(screen.queryByText('Streetlight outage')).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([url]) => url.includes('/complaint-1') && url.includes('/escalate'))).toBe(true)
  })

  it('clears selected details when the backend denies detail access', async () => {
    const fetchMock = vi.fn((request: string) => {
      if (request.endsWith('/complaint-1')) return Promise.resolve(new Response(JSON.stringify({ error: 'Not found' }), { status: 404, headers: { 'Content-Type': 'application/json' } }))
      return Promise.resolve(jsonResponse([complaint]))
    })
    vi.stubGlobal('fetch', fetchMock)
    const user = userEvent.setup()
    renderComplaints({ id: 'staff-1', displayName: 'Local staff', email: 'staff@example.test', roles: ['LOCAL_STAFF'] })

    await user.click(await screen.findByRole('button', { name: /SC-001/i }))

    expect(await screen.findByText('Select a complaint')).toBeVisible()
    expect(screen.queryByText('Streetlight outage')).not.toBeInTheDocument()
  })
})
