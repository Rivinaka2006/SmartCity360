import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearCsrfToken } from '@/lib/api/csrf-store'
import { assignComplaintToSelf, escalateComplaint, resolveComplaint, startComplaint } from './complaint-api'

function stubMutationResponses(fetchMock: ReturnType<typeof vi.fn>) {
  fetchMock
    .mockResolvedValueOnce(new Response(JSON.stringify({ token: 'csrf-token' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    .mockResolvedValueOnce(new Response(JSON.stringify({ id: 'complaint-1' }), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
}

afterEach(() => {
  clearCsrfToken()
  vi.unstubAllGlobals()
})

describe('complaint workflow API contracts', () => {
  it('preserves the assignment request contract', async () => {
    const fetchMock = vi.fn()
    stubMutationResponses(fetchMock)
    vi.stubGlobal('fetch', fetchMock)

    await assignComplaintToSelf('complaint/1', 'staff-1')

    expect(fetchMock.mock.calls[1]).toEqual([
      '/smartcity360/api/v1/complaints/complaint%2F1/assign',
      expect.objectContaining({ method: 'POST', body: '{"assigneeId":"staff-1"}' }),
    ])
  })

  it('preserves the start request contract', async () => {
    const fetchMock = vi.fn()
    stubMutationResponses(fetchMock)
    vi.stubGlobal('fetch', fetchMock)

    await startComplaint('complaint-1')

    expect(fetchMock.mock.calls[1]).toEqual([
      '/smartcity360/api/v1/complaints/complaint-1/start',
      expect.objectContaining({ method: 'POST' }),
    ])
  })

  it('preserves the escalation request contract', async () => {
    const fetchMock = vi.fn()
    stubMutationResponses(fetchMock)
    vi.stubGlobal('fetch', fetchMock)

    await escalateComplaint('complaint-1', 'SUPERVISOR', 'Requires review')

    expect(fetchMock.mock.calls[1]).toEqual([
      '/smartcity360/api/v1/complaints/complaint-1/escalate',
      expect.objectContaining({
        method: 'POST',
        body: '{"expectedLevel":"SUPERVISOR","reason":"Requires review"}',
      }),
    ])
  })

  it('preserves the resolution request contract', async () => {
    const fetchMock = vi.fn()
    stubMutationResponses(fetchMock)
    vi.stubGlobal('fetch', fetchMock)

    await resolveComplaint('complaint-1', 'Lamp repaired')

    expect(fetchMock.mock.calls[1]).toEqual([
      '/smartcity360/api/v1/complaints/complaint-1/resolve',
      expect.objectContaining({ method: 'POST', body: '{"note":"Lamp repaired"}' }),
    ])
  })
})
