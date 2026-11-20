import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '@/features/auth/auth-state'
import { StreetlightsPage } from './streetlights-page'
import type { Streetlight } from './streetlight-api'

const streetZone = {
  id: '00000000-0000-0000-0000-000000000002',
  parentId: '00000000-0000-0000-0000-000000000001',
  type: 'STREET',
  code: 'S002',
  name: 'Peradeniya Road',
  status: 'ACTIVE',
  createdAt: '2026-11-07T00:00:00Z',
  updatedAt: '2026-11-07T00:00:00Z',
}

const profile = {
  id: '00000000-0000-0000-0000-000000000003',
  profileCode: 'TEST-LED-60W',
  profileVersion: 1,
  manufacturer: 'SmartCity',
  model: 'LED-60W',
  wattage: 60,
  defaultBrightnessPct: 75,
  controllerType: 'DALI',
  firmwareFamily: 'v1',
  configurationJson: '{}',
  active: true,
  createdAt: '2026-11-07T00:00:00Z',
}

const streetlight: Streetlight = {
  id: 'light-1',
  assetTag: 'SL-001',
  profileId: profile.id,
  profileVersion: 1,
  streetZoneId: streetZone.id,
  latitude: 7.25,
  longitude: 80.6,
  addressLabel: null,
  operationalState: 'OFF',
  brightnessOverridePct: null,
  effectiveBrightnessPct: 75,
  installedOn: null,
  lastSeenAt: null,
  registeredAt: '2026-11-07T00:00:00Z',
}

function renderStreetlights() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={{
        status: 'authenticated',
        user: { id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] },
        error: null,
        login: vi.fn(),
        logout: vi.fn(),
      }}>
        <StreetlightsPage />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

function stubRequests(streetZones = [streetZone], selectedStreetlight = streetlight) {
  const fetchMock = vi.fn((request: string, init?: RequestInit) => {
    if (request.endsWith('/auth/csrf')) return Promise.resolve(jsonResponse({ token: 'csrf-token' }))
    if (request.includes('/zones/parents?')) return Promise.resolve(jsonResponse(streetZones))
    if (request.includes('/lighting-profiles?')) return Promise.resolve(jsonResponse([profile]))
    if (request.endsWith('/streetlights/stats')) return Promise.resolve(jsonResponse({ persistedStreetlights: 0, distinctActiveProfiles: 0, cachedFlyweights: 0, cacheCapacity: 100, cacheHits: 0, cacheMisses: 0, cacheEvictions: 0, contextsProcessed: 0, contextToFlyweightRatio: 0 }))
    if (request.endsWith('/streetlights') && init?.method === 'POST') return Promise.resolve(jsonResponse(streetlight, 201))
    if (request.endsWith('/streetlights/light-1/profile')) return Promise.resolve(jsonResponse(profile))
    if (request.endsWith('/streetlights/light-1')) return Promise.resolve(jsonResponse(selectedStreetlight))
    if (request.includes('/streetlights?')) return Promise.resolve(jsonResponse([streetlight]))
    return Promise.resolve(jsonResponse([]))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('StreetlightsPage registration zone selection', () => {
  it('submits the selected STREET zone UUID rather than its displayed zone code', async () => {
    const fetchMock = stubRequests()
    const user = userEvent.setup()
    renderStreetlights()

    expect(await screen.findByText('Zone results 1-1')).toBeVisible()
    await user.type(await screen.findByLabelText('Asset tag'), 'SL-001')
    await user.selectOptions(screen.getByLabelText('Active lighting profile'), profile.id)
    await user.selectOptions(await screen.findByLabelText(/Street zone/), streetZone.id)
    await user.type(screen.getByLabelText('Latitude'), '7.25')
    await user.type(screen.getByLabelText('Longitude'), '80.6')
    await user.click(screen.getByRole('button', { name: 'Register' }))

    const registrationCall = fetchMock.mock.calls.find(([url, init]) => url.endsWith('/streetlights') && init?.method === 'POST')
    expect(registrationCall?.[1]).toEqual(expect.objectContaining({
      body: expect.stringContaining(`"streetZoneId":"${streetZone.id}"`),
    }))
    expect(registrationCall?.[1]).not.toEqual(expect.objectContaining({
      body: expect.stringContaining('S002'),
    }))
    expect(screen.getByRole('option', { name: 'Peradeniya Road (S002)' })).toBeVisible()
  })

  it('prevents registration when no active STREET zone is available', async () => {
    const fetchMock = stubRequests([])
    renderStreetlights()

    expect(await screen.findByText('No active STREET zones found')).toBeVisible()
    expect(screen.getByRole('button', { name: 'Register' })).toBeDisabled()
    expect(fetchMock.mock.calls.some(([url, init]) => url.endsWith('/streetlights') && init?.method === 'POST')).toBe(false)
  })
})

describe('StreetlightsPage brightness override display', () => {
  it.each([
    [{ ...streetlight, brightnessOverridePct: undefined }, '75% effective · No override'],
    [{ ...streetlight, brightnessOverridePct: null }, '75% effective · No override'],
    [{ ...streetlight, brightnessOverridePct: 0 }, '75% effective · 0% override'],
    [{ ...streetlight, brightnessOverridePct: 40 }, '75% effective · 40% override'],
  ])('renders %s without an undefined override label', async (selectedStreetlight, expectedBrightness) => {
    stubRequests([streetZone], selectedStreetlight)
    const user = userEvent.setup()
    renderStreetlights()

    await user.click(await screen.findByRole('button', { name: /SL-001/ }))

    expect(await screen.findByText(expectedBrightness)).toBeVisible()
    expect(screen.queryByText(/undefined% override/)).not.toBeInTheDocument()
  })
})
