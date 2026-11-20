import { afterEach, describe, expect, it, vi } from 'vitest'
import { fetchDashboardSummary } from './dashboard-api'

afterEach(() => vi.unstubAllGlobals())

describe('dashboard-api', () => {
  it('requests the secured dashboard summary endpoint with same-origin credentials', async () => {
    const response = {
      asOf: '2026-11-06T12:00:00Z',
      zones: { total: 7, buildings: 3 },
      power: { on: 2, off: 0, unknown: 1 },
      complaints: { total: 4, open: 4, inProgress: 0, escalated: 0, resolved: 0, rejected: 0 },
      streetlights: { total: 2, on: 1, off: 1, fault: 0, maintenance: 0 },
      energy: { from: '2026-11-05T12:00:00Z', to: '2026-11-06T12:00:00Z', consumptionKwh: null, readingCount: 0 },
      policies: { total: 1, draft: 1, active: 0, inactive: 0 },
    }
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(response), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchDashboardSummary()).resolves.toEqual(response)
    expect(fetchMock).toHaveBeenCalledWith('/smartcity360/api/v1/dashboard/summary', expect.objectContaining({
      method: 'GET',
      credentials: 'same-origin',
    }))
  })

  it('rejects a successful response with a missing numeric field', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      asOf: '2026-11-06T12:00:00Z',
      zones: { total: 7, buildings: 3 },
      power: { on: 2, off: 0, unknown: 1 },
      complaints: { total: 4, open: 2, inProgress: 1, escalated: 1, resolved: 0, rejected: 0 },
      streetlights: { total: 8, on: 5, off: 1, fault: 1, maintenance: 1 },
      energy: { from: '2026-11-05T12:00:00Z', to: '2026-11-06T12:00:00Z', readingCount: 12 },
      policies: { total: 2, draft: 1, active: 1, inactive: 0 },
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(fetchDashboardSummary()).rejects.toMatchObject({ code: 'UNEXPECTED_RESPONSE', status: 200 })
  })

  it('rejects a malformed successful response instead of returning an unsafe cast', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ energy: null }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    })))

    await expect(fetchDashboardSummary()).rejects.toMatchObject({ code: 'UNEXPECTED_RESPONSE', status: 200 })
  })
})
