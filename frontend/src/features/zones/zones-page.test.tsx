import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { ZonesPage } from './zones-page'

const listZones = vi.fn()
const listZoneParents = vi.fn()

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ user: { roles: ['ADMIN'], displayName: 'Admin', email: 'admin@example.test' } }),
}))

vi.mock('./zone-api', () => ({
  createZone: vi.fn(),
  getZoneCommands: vi.fn(),
  getZoneTree: vi.fn(),
  getZoneUsage: vi.fn(),
  listZones: (...args: unknown[]) => listZones(...args),
  listZoneParents: (...args: unknown[]) => listZoneParents(...args),
  runZonePowerCommand: vi.fn(),
  updateZone: vi.fn(),
}))

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={queryClient}><ZonesPage /></QueryClientProvider>)
}

describe('ZonesPage', () => {
  it('shows CITY as the initial creation type and an empty state when no zones exist', async () => {
    listZones.mockResolvedValueOnce([])
    renderPage()

    expect(await screen.findByText('No zones on this page')).toBeVisible()
    expect(screen.getByLabelText('Type')).toHaveValue('CITY')
    expect(screen.getByText('CITY zones are root zones and do not have a parent.')).toBeVisible()
  })

  it('shows a safe retryable error when the zone list fails', async () => {
    listZones.mockRejectedValueOnce(new Error('backend failure'))
    renderPage()

    expect(await screen.findByText('Zones unavailable')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Try again' })).toBeVisible()
    await waitFor(() => expect(screen.getByText('backend failure')).toBeVisible())
  })
})
