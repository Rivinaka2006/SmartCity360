import { apiRequest } from '@/lib/api/client'
import { apiMutation } from '@/lib/api/mutations'

export type EnergyRun = {
  id: string
  meterCode: string
  decoratorChain: string
  inputBytes: number
  outputBytes: number | null
  status: string
  durationMs: number | null
  errorCode: string | null
  executedAt: string
}

export type IntegratedEnergyDemoResult = {
  runId: string
  status: string
  meterCode: string
  submittedPayload: string
  decoratorLayers: string[]
  inputBytes: number
  encryptedOutputBytes: number
  protectedPayloadBase64: string
  meterAuthenticated: boolean
  encryptionCompleted: boolean
  processingRecordPersisted: boolean
}

const root = '/smartcity360/api/v1/energy/streams'

export function listEnergyRuns(offset: number, limit: number, signal?: AbortSignal) {
  const params = new URLSearchParams({ offset: String(offset), limit: String(limit) })
  return apiRequest<EnergyRun[]>(`${root}/runs?${params}`, { signal })
}

export function getEnergyRun(id: string, signal?: AbortSignal) {
  return apiRequest<EnergyRun>(`${root}/runs/${encodeURIComponent(id)}`, { signal })
}

export function verifyEnergyEnvelope(envelopeBase64: string) {
  return apiMutation<{ payloadBase64: string }>(`${root}/verify`, {
    method: 'POST',
    body: { envelopeBase64 },
  })
}

export function getIntegratedEnergyDemoAvailability(signal?: AbortSignal) {
  return apiRequest<{ enabled: boolean; prepared: boolean }>(`${root}/demo/process-reading/status`, { signal })
}

export function processIntegratedEnergyDemoReading(consumptionKwh: string) {
  return apiMutation<IntegratedEnergyDemoResult>(`${root}/demo/process-reading`, {
    method: 'POST',
    body: { consumptionKwh },
  })
}
