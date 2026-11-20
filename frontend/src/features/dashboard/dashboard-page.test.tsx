import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router-dom'
import type { CurrentUser } from '@/lib/api/contracts'
import { AuthContext } from '@/features/auth/auth-state'
import { DashboardPage } from './dashboard-page'

const userProfile: CurrentUser = {
  id: '0f6ae02d-54cc-44ec-909f-5758c3b30bd9',
  displayName: 'Asha Perera',
  email: 'asha@example.test',
  roles: ['CITY_MANAGER'],
}

function renderDashboard(user = userProfile) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={{ status: 'authenticated', user, error: null, login: vi.fn(), logout: vi.fn() }}>
        <MemoryRouter><DashboardPage /></MemoryRouter>
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

afterEach(() => vi.unstubAllGlobals())

describe('DashboardPage', () => {
  const summary = {
    asOf: '2026-11-06T12:00:00Z',
    zones: { total: 7, buildings: 3 },
    power: { on: 2, off: 0, unknown: 1 },
    complaints: { total: 4, open: 2, inProgress: 1, escalated: 1, resolved: 0, rejected: 0 },
    streetlights: { total: 8, on: 5, off: 1, fault: 1, maintenance: 1 },
    energy: { from: '2026-11-05T12:00:00Z', to: '2026-11-06T12:00:00Z', consumptionKwh: 128.5, readingCount: 12 },
    policies: { total: 2, draft: 1, active: 1, inactive: 0 },
  }

  it('shows real operational metrics, backend health, profile data, and navigation', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP', service: 'SmartCity360 API' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify(summary), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    renderDashboard()

    expect(await screen.findByText('SmartCity360 API')).toBeVisible()
    expect(screen.getByText('UP')).toBeVisible()
    expect(screen.getByText('Asha Perera')).toBeVisible()
    expect(screen.getByText('CITY MANAGER')).toBeVisible()
    expect(screen.getByText('Operations command center')).toBeVisible()
    expect(screen.getByRole('group', { name: 'Key operational indicators' })).toBeVisible()
    expect(screen.getByText('128.50 kWh')).toBeVisible()
    expect(screen.getAllByText('2').length).toBeGreaterThanOrEqual(1)
    expect(screen.getByText('State unavailable')).toBeVisible()
    expect(screen.getByText('Open')).toBeVisible()
    expect(screen.getByText('Streetlight network')).toBeVisible()
    expect(screen.getByRole('link', { name: 'Energy streams' })).toBeVisible()
  })

  it('provides an accessible pending state while dashboard data is unresolved', () => {
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => undefined)))

    renderDashboard()

    expect(screen.getAllByRole('status').some((element) => element.textContent?.includes('Loading operations summary'))).toBe(true)
  })

  it('shows a safe retryable error when dashboard data fails', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP', service: 'SmartCity360 API' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response('<html>Unavailable</html>', { status: 503, headers: { 'Content-Type': 'text/html' } })))

    renderDashboard()

    expect(await screen.findByRole('alert')).toHaveTextContent('The operations summary could not be reached.')
    expect(screen.queryByText('Unavailable')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('distinguishes no energy readings from measured zero consumption', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP', service: 'SmartCity360 API' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ ...summary, energy: { ...summary.energy, consumptionKwh: null, readingCount: 0 } }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    renderDashboard()

    expect(await screen.findByText('No readings')).toBeVisible()
    expect(screen.getByText('No energy measurements in the reporting period.')).toBeVisible()
  })

  it('shows a retryable error when a successful summary response is incompatible', async () => {
    vi.stubGlobal('fetch', vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ status: 'UP', service: 'SmartCity360 API' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ energy: {} }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    renderDashboard()

    expect(await screen.findByRole('alert')).toHaveTextContent('The operations summary could not be reached.')
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
  })

  it('shows a resident dashboard without requesting the restricted operations summary', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({ status: 'UP', service: 'SmartCity360 API' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    renderDashboard({ ...userProfile, roles: ['RESIDENT'] })

    expect(await screen.findByText('Welcome to SmartCity360')).toBeVisible()
    expect(screen.getByRole('link', { name: 'My complaints' })).toBeVisible()
    expect(fetchMock.mock.calls.map(([url]) => url)).not.toContain('/smartcity360/api/v1/dashboard/summary')
  })
})
