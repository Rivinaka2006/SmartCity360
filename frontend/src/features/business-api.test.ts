import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '@/lib/api/csrf-store'
import { listMyComplaints, submitComplaint } from './complaints/complaint-api'
import { listEnergyRuns, verifyEnergyEnvelope } from './energy/energy-api'
import { createPolicy, evaluatePolicy } from './policies/policy-api'
import { listStreetlights, setStreetlightBrightness } from './streetlights/streetlight-api'
import { createZone, getZoneUsage } from './zones/zone-api'

afterEach(() => {
  clearCsrfToken()
  vi.unstubAllGlobals()
})

describe('business API contracts', () => {
  it('uses the backend zone paths and query parameters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await getZoneUsage('zone/id', '2026-11-01T00:00:00Z', '2026-11-02T00:00:00Z')

    expect(fetchMock.mock.calls[0]?.[0]).toBe(
      '/smartcity360/api/v1/zones/zone%2Fid/usage?from=2026-11-01T00%3A00%3A00Z&to=2026-11-02T00%3A00%3A00Z',
    )
  })

  it('sends zone mutations through the session CSRF flow using the exact DTO', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'zone-1' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await createZone({ type: 'DISTRICT', code: 'NORTH', name: 'North', parentId: 'city-1' })

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual([
      '/smartcity360/api/v1/auth/csrf',
      '/smartcity360/api/v1/zones',
    ])
    const [, request] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(request.body).toBe('{"type":"DISTRICT","code":"NORTH","name":"North","parentId":"city-1"}')
    expect(request.headers).toEqual(expect.objectContaining({ 'X-CSRF-Token': 'csrf-token' }))
  })

  it('uses resident complaint listing and submission paths defined by the backend', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await listMyComplaints(25, 25)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/smartcity360/api/v1/complaints/my?offset=25&limit=25')

    fetchMock.mockReset()
    fetchMock
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'complaint-1' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
    await submitComplaint({ title: 'Street light out', description: 'Lamp is not working', severity: 'MEDIUM', zoneId: 'zone-1' })
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/smartcity360/api/v1/complaints')
  })

  it('uses the streetlight list path with backend pagination', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await listStreetlights(50, 25)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/smartcity360/api/v1/streetlights?offset=50&limit=25')
  })

  it('uses backend-defined policy routes and exposes evaluation DTOs returned with HTTP 400', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'policy-1' }), { status: 201, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'evaluation-1', status: 'ERROR', errorCode: 'UNKNOWN_VARIABLE' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }))
    vi.stubGlobal('fetch', fetchMock)

    await createPolicy({ policyCode: 'QUIET_HOURS', name: 'Quiet hours', domain: 'NOISE', description: 'Noise limits' })
    await expect(evaluatePolicy('policy-1', { HOUR: 22 })).resolves.toMatchObject({ status: 'ERROR', errorCode: 'UNKNOWN_VARIABLE' })
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/smartcity360/api/v1/policies')
    expect(fetchMock.mock.calls[2]?.[0]).toBe('/smartcity360/api/v1/policies/policy-1/evaluate')
  })

  it('sends streetlight brightness using the backend request shape', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ effectiveBrightnessPct: 40 }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await setStreetlightBrightness('light-1', 40)

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/smartcity360/api/v1/streetlights/light-1/brightness')
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({ method: 'POST', body: '{"brightnessPct":40}' }))
  })

  it('uses energy run history only and does not fabricate a processing request', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await listEnergyRuns(0, 25)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/smartcity360/api/v1/energy/streams/runs?offset=0&limit=25')
    expect(fetchMock.mock.calls[0]?.[1]).toEqual(expect.objectContaining({ method: 'GET' }))
  })

  it('sends protected-envelope verification to the backend without client-side keys', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      .mockResolvedValueOnce(new Response(JSON.stringify({ payloadBase64: 'cGF5bG9hZA==' }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await expect(verifyEnergyEnvelope('ZW52ZWxvcGU=')).resolves.toEqual({ payloadBase64: 'cGF5bG9hZA==' })

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/smartcity360/api/v1/energy/streams/verify')
    expect(fetchMock.mock.calls[1]?.[1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: '{"envelopeBase64":"ZW52ZWxvcGU="}',
      headers: expect.objectContaining({ 'X-CSRF-Token': 'csrf-token' }),
    }))
  })
})
