import { describe, expect, it, vi } from 'vitest'
import { listComplaintLocations } from './complaint-location-api'

describe('complaint location API', () => {
  it('uses resident-authorized search and pagination parameters', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response('[]', { status: 200, headers: { 'Content-Type': 'application/json' } }))
    vi.stubGlobal('fetch', fetchMock)

    await listComplaintLocations('Colombo', 25, 25)

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/smartcity360/api/v1/zones/complaint-locations?search=Colombo&offset=25&limit=25')
  })
})
