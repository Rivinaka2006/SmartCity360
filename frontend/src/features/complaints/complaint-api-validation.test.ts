import { describe, expect, it, vi } from 'vitest'
import { getComplaint } from './complaint-api'

describe('complaint API response validation', () => {
  it('accepts a real-world-shaped OPEN unassigned LOCAL_STAFF complaint', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'complaint-1',
      referenceNo: 'SC-001',
      residentId: 'resident-1',
      zoneId: 'zone-1',
      title: 'Streetlight inspection test',
      description: 'Controlled test',
      severity: 'LOW',
      status: 'OPEN',
      assignedToUserId: null,
      currentHandlerLevel: 'LOCAL_STAFF',
      createdAt: '2026-11-07T00:00:00+05:30',
      updatedAt: '2026-11-07T00:00:00+05:30',
      resolvedAt: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(getComplaint('complaint-1')).resolves.toMatchObject({
      assignedToUserId: null,
      currentHandlerLevel: 'LOCAL_STAFF',
      status: 'OPEN',
    })
  })

  it('accepts an assigned complaint with its actual UUID', async () => {
    const assignedUserId = '00000000-0000-0000-0000-000000000099'
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'complaint-1', referenceNo: 'SC-001', residentId: 'resident-1', zoneId: 'zone-1',
      title: 'Streetlight inspection test', description: 'Controlled test', severity: 'LOW', status: 'OPEN',
      assignedToUserId: assignedUserId, currentHandlerLevel: 'LOCAL_STAFF',
      createdAt: '2026-11-07T00:00:00+05:30', updatedAt: '2026-11-07T00:00:00+05:30', resolvedAt: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(getComplaint('complaint-1')).resolves.toMatchObject({ assignedToUserId: assignedUserId })
  })

  it('accepts a valid legacy-shaped detail response when optional display labels are absent', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'complaint-1', referenceNo: 'SC-001', residentId: 'resident-1', zoneId: 'zone-1',
      title: 'Streetlight inspection test', description: 'Controlled test', severity: 'LOW', status: 'OPEN',
      assignedToUserId: null, currentHandlerLevel: 'LOCAL_STAFF',
      createdAt: '2026-11-07T00:00:00+05:30', updatedAt: '2026-11-07T00:00:00+05:30', resolvedAt: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    const response = await getComplaint('complaint-1')

    expect(response.assignedToUserId).toBeNull()
    expect('zoneName' in response).toBe(false)
    expect('assignedUserDisplayName' in response).toBe(false)
  })

  it('rejects malformed optional display labels without accepting malformed authorization fields', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'complaint-1', referenceNo: 'SC-001', residentId: 'resident-1', zoneId: 'zone-1',
      zoneName: 42,
      title: 'Streetlight inspection test', description: 'Controlled test', severity: 'LOW', status: 'OPEN',
      assignedToUserId: null, currentHandlerLevel: 'LOCAL_STAFF',
      createdAt: '2026-11-07T00:00:00+05:30', updatedAt: '2026-11-07T00:00:00+05:30', resolvedAt: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(getComplaint('complaint-1')).rejects.toMatchObject({ code: 'UNEXPECTED_RESPONSE' })
  })

  it('rejects a complaint detail response that omits assignedToUserId', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({
      id: 'complaint-1',
      referenceNo: 'SC-001',
      residentId: 'resident-1',
      zoneId: 'zone-1',
      title: 'Streetlight inspection test',
      description: 'Controlled test',
      severity: 'LOW',
      status: 'OPEN',
      currentHandlerLevel: 'LOCAL_STAFF',
      createdAt: '2026-11-07T00:00:00Z',
      updatedAt: '2026-11-07T00:00:00Z',
      resolvedAt: null,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))

    await expect(getComplaint('complaint-1')).rejects.toMatchObject({
      code: 'UNEXPECTED_RESPONSE',
    })
  })
})
