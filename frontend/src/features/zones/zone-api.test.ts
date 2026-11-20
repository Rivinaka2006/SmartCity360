import { describe, expect, it, vi } from 'vitest'
import { listZoneParents } from './zone-api'

describe('zone parent lookup API', () => {
  it('uses the server-side type, search, and pagination parameters', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('[]', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    })))

    await listZoneParents('CITY', 'Central City', 25, 25)

    expect(fetch).toHaveBeenCalledWith(
      '/smartcity360/api/v1/zones/parents?type=CITY&search=Central+City&offset=25&limit=25',
      expect.objectContaining({ credentials: 'same-origin' }),
    )
    vi.unstubAllGlobals()
  })
})
