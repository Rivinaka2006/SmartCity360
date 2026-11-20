import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { CurrentUser } from '@/lib/api/contracts'
import { AuthContext } from '@/features/auth/auth-state'
import { EnergyPage } from './energy-page'

const integratedPayload = '{"meterCode":"ACADEMIC-VIVA-METER","consumptionKwh":1.25,"intervalMinutes":15}'
const compressedIntegratedPayload = 'H4sIAAAAAAAACqtWyk0tSS1yzk9JVbJScnR2dHH19XTWDfMMc9T1dQ1xDVLSUUrOzysuzS0oyczP8y7PULIy1DMy1VHKzCtJLSpLzPHNzCstSS1WsjI0rQUAe4kHqU4AAAA='
const integratedResult = {
  runId: 'integrated-run-1',
  status: 'SUCCESS',
  meterCode: 'ACADEMIC-VIVA-METER',
  submittedPayload: integratedPayload,
  decoratorLayers: ['RAW', 'AUTHENTICATION', 'COMPRESSION', 'ENCRYPTION'],
  inputBytes: new TextEncoder().encode(integratedPayload).length,
  encryptedOutputBytes: 168,
  protectedPayloadBase64: 'U0MzNg==',
  meterAuthenticated: true,
  encryptionCompleted: true,
  processingRecordPersisted: true,
}

function renderEnergyPage(roles: CurrentUser['roles']) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const user: CurrentUser = {
    id: 'user-1',
    displayName: 'Energy user',
    email: 'energy@example.test',
    roles,
  }
  return render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={{ status: 'authenticated', user, error: null, login: vi.fn(), logout: vi.fn() }}>
        <EnergyPage />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

function response(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function stubEnergyRequests(options: {
  runs?: unknown[]
  verification?: unknown
  integratedAvailability?: { enabled: boolean; prepared: boolean }
  integratedResult?: unknown
  integratedStatus?: number
} = {}) {
  let integratedProcessed = false
  const persistedRun = {
    id: 'integrated-run-1',
    meterCode: 'ACADEMIC-VIVA-METER',
    decoratorChain: '["RAW","AUTHENTICATION","COMPRESSION","ENCRYPTION"]',
    inputBytes: integratedResult.inputBytes,
    outputBytes: integratedResult.encryptedOutputBytes,
    status: 'SUCCESS',
    durationMs: 3,
    errorCode: null,
    executedAt: '2026-11-09T00:00:00Z',
  }
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    if (url.includes('/energy/streams/runs?')) return response(options.runs ?? (integratedProcessed ? [persistedRun] : []))
    if (url.endsWith('/energy/streams/demo/process-reading/status')) {
      return response(options.integratedAvailability ?? { enabled: true, prepared: true })
    }
    if (url.endsWith('/auth/csrf')) return response({ token: 'csrf-demo-token' })
    if (url.endsWith('/energy/streams/demo/process-reading') && init?.method === 'POST') {
      integratedProcessed = true
      return response(options.integratedResult ?? integratedResult, options.integratedStatus ?? 200)
    }
    if (url.endsWith('/energy/streams/verify')) return response(options.verification ?? { payloadBase64: 'cmVhZGluZw==' })
    return response({ error: 'Unexpected test request' }, 404)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

afterEach(() => vi.unstubAllGlobals())

describe('EnergyPage', () => {
  it('removes the local decorator demonstration and its unavailable notice', async () => {
    const fetchMock = stubEnergyRequests()
    renderEnergyPage(['ADMIN'])

    expect(await screen.findByRole('heading', { name: 'Processing history' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Processing layers' })).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Verify protected payload' })).toBeVisible()
    expect(await screen.findByRole('heading', { name: 'Integrated Meter Processing' })).toBeVisible()
    expect(screen.queryByText('Decorator Demonstration — Local Academic Demo')).not.toBeInTheDocument()
    expect(screen.queryByText('Decorator demonstration is unavailable in this environment.')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Run Demonstration' })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/energy/streams/demo/status'))).toBe(false)
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/energy/streams/demo'))).toBe(false)
  })

  it('does not request integrated demo readiness or render controls for a non-ADMIN', async () => {
    const fetchMock = stubEnergyRequests()
    renderEnergyPage(['CITY_MANAGER'])

    expect(await screen.findByText('Processing history')).toBeVisible()
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/energy/streams/demo/process-reading/status'))).toBe(false)
  })

  it('preserves processing history and protected payload verification', async () => {
    const fetchMock = stubEnergyRequests({
      runs: [{
        id: 'run-1',
        meterCode: 'METER-1',
        decoratorChain: '["RAW","AUTHENTICATION","COMPRESSION","ENCRYPTION"]',
        inputBytes: 12,
        outputBytes: 20,
        status: 'SUCCESS',
        durationMs: 2,
        errorCode: null,
        executedAt: '2026-11-09T00:00:00Z',
      }],
    })
    const user = userEvent.setup()
    renderEnergyPage(['ADMIN'])

    expect(await screen.findByText('METER-1')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Verify protected payload' })).toBeVisible()
    await user.type(screen.getByLabelText('Protected envelope (Base64)'), 'U0MzNg==')
    await user.click(screen.getByRole('button', { name: 'Verify on backend' }))

    expect(await screen.findByText('Encrypted envelope verified and decrypted')).toBeVisible()
    await waitFor(() => {
      expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/energy/streams/verify'))).toBe(true)
    })
  })

  it('processes through the integrated endpoint, refreshes history, and recovers the verified GZIP reading', async () => {
    const fetchMock = stubEnergyRequests({
      verification: { payloadBase64: compressedIntegratedPayload },
    })
    const user = userEvent.setup()
    renderEnergyPage(['ADMIN'])

    await user.click(await screen.findByRole('button', { name: 'Process Demo Meter Reading' }))

    expect(await screen.findAllByText('integrated-run-1')).not.toHaveLength(0)
    expect(screen.getByText('Meter request authenticated')).toBeVisible()
    expect(await screen.findByText('ACADEMIC-VIVA-METER')).toBeVisible()
    const processCall = fetchMock.mock.calls.find(([input]) => String(input).endsWith('/energy/streams/demo/process-reading'))
    expect(processCall?.[1]?.method).toBe('POST')
    expect(JSON.parse(String(processCall?.[1]?.body))).toEqual({ consumptionKwh: '1.25' })
    expect(new Headers(processCall?.[1]?.headers).get('X-CSRF-Token')).toBe('csrf-demo-token')

    await user.click(screen.getByRole('button', { name: 'Use in verification' }))
    expect(screen.getByLabelText('Protected envelope (Base64)')).toHaveValue(integratedResult.protectedPayloadBase64)
    await user.click(screen.getByRole('button', { name: 'Verify on backend' }))

    expect(await screen.findByText('GZIP payload recovered and matched the submitted reading')).toBeVisible()
    expect(screen.getByLabelText('Recovered original reading (JSON)')).toHaveValue(integratedPayload)
    await waitFor(() => {
      expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('/energy/streams/runs?')).length).toBeGreaterThan(1)
    })
    expect(fetchMock.mock.calls.some(([input]) => String(input).endsWith('/energy/streams/verify'))).toBe(true)
  })

  it('shows an explicit preparation-required state and does not offer a fake processing result', async () => {
    stubEnergyRequests({ integratedAvailability: { enabled: true, prepared: false } })
    renderEnergyPage(['ADMIN'])

    expect(await screen.findByText(/Demo environment preparation required/)).toBeVisible()
    expect(screen.queryByRole('button', { name: 'Process Demo Meter Reading' })).not.toBeInTheDocument()
  })

  it('does not present a success result when integrated processing fails', async () => {
    stubEnergyRequests({ integratedStatus: 503 })
    const user = userEvent.setup()
    renderEnergyPage(['ADMIN'])

    await user.click(await screen.findByRole('button', { name: 'Process Demo Meter Reading' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('service is temporarily unavailable')
    expect(screen.queryByText('Integrated processing result')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Use in verification' })).not.toBeInTheDocument()
  })
})
