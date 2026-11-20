import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/api-error'
import { useAuth } from '@/features/auth/use-auth'
import {
  getIntegratedEnergyDemoAvailability,
  listEnergyRuns,
  processIntegratedEnergyDemoReading,
  verifyEnergyEnvelope,
} from './energy-api'
import { recoverDemoPayload } from './energy-recovery'

const pageSize = 25
const consumptionPattern = /^[0-9]{1,9}(\.[0-9]{1,6})?$/

function chainLayers(chain: string) {
  try {
    const parsed: unknown = JSON.parse(chain)
    if (Array.isArray(parsed) && parsed.every((layer): layer is string => typeof layer === 'string')) return parsed
  } catch {
    return chain.split(/[,>\s]+/).filter(Boolean)
  }
  return [chain]
}

function isDemoReading(value: unknown): value is { meterCode: string; consumptionKwh: number } {
  if (typeof value !== 'object' || value === null) return false
  return 'meterCode' in value
    && typeof value.meterCode === 'string'
    && 'consumptionKwh' in value
    && typeof value.consumptionKwh === 'number'
    && Number.isFinite(value.consumptionKwh)
}

function isIntegratedResultConfirmed(result: {
  status: string
  runId: string
  meterCode: string
  protectedPayloadBase64: string
  decoratorLayers: string[]
  meterAuthenticated: boolean
  encryptionCompleted: boolean
  processingRecordPersisted: boolean
}) {
  return typeof result.runId === 'string'
    && result.runId.length > 0
    && typeof result.meterCode === 'string'
    && typeof result.protectedPayloadBase64 === 'string'
    && result.protectedPayloadBase64.length > 0
    && Array.isArray(result.decoratorLayers)
    && result.decoratorLayers.every((layer) => typeof layer === 'string')
    && result.status === 'SUCCESS'
    && result.meterAuthenticated
    && result.encryptionCompleted
    && result.processingRecordPersisted
    && ['AUTHENTICATION', 'COMPRESSION', 'ENCRYPTION'].every((layer) => result.decoratorLayers.includes(layer))
}

export function EnergyPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [offset, setOffset] = useState(0)
  const [envelope, setEnvelope] = useState('')
  const [verificationError, setVerificationError] = useState('')
  const [integratedConsumption, setIntegratedConsumption] = useState('1.25')
  const [recoveredDemoPayload, setRecoveredDemoPayload] = useState('')
  const [recoveryError, setRecoveryError] = useState('')
  const runs = useQuery({ queryKey: ['energy-runs', offset], queryFn: ({ signal }) => listEnergyRuns(offset, pageSize, signal) })
  const canDemonstrate = user?.roles.includes('ADMIN') ?? false
  const integratedDemoAvailability = useQuery({
    queryKey: ['energy-stream-integrated-demo-availability'],
    queryFn: ({ signal }) => getIntegratedEnergyDemoAvailability(signal),
    enabled: canDemonstrate,
    retry: false,
    staleTime: 60_000,
  })
  const integratedProcessing = useMutation({
    mutationFn: processIntegratedEnergyDemoReading,
    onSuccess: async () => {
      setOffset(0)
      await queryClient.invalidateQueries({ queryKey: ['energy-runs'] })
    },
  })
  const verify = useMutation({
    mutationFn: verifyEnergyEnvelope,
    onSuccess: async (result, submittedEnvelope) => {
      setVerificationError('')
      setRecoveredDemoPayload('')
      setRecoveryError('')
      const demoResult = integratedProcessing.data
      if (!demoResult
        || !isIntegratedResultConfirmed(demoResult)
        || submittedEnvelope !== demoResult.protectedPayloadBase64) return
      try {
        const recovered = await recoverDemoPayload(result.payloadBase64)
        if (recovered !== demoResult.submittedPayload) {
          setRecoveryError('The decrypted GZIP payload did not match the submitted demo reading.')
          return
        }
        const parsed: unknown = JSON.parse(recovered)
        if (!isDemoReading(parsed) || parsed.meterCode !== demoResult.meterCode) {
          setRecoveryError('The decrypted payload was not the expected demo meter reading.')
          return
        }
        setRecoveredDemoPayload(recovered)
      } catch (error) {
        setRecoveryError(error instanceof Error ? error.message : 'The decrypted demo payload could not be recovered.')
      }
    },
    onError: (error) => setVerificationError(error.message),
  })
  const canVerify = user?.roles.some((role) => role === 'ADMIN' || role === 'CITY_MANAGER') ?? false
  const numericIntegratedConsumption = Number(integratedConsumption)
  const integratedInputValid = consumptionPattern.test(integratedConsumption)
    && Number.isFinite(numericIntegratedConsumption)
    && numericIntegratedConsumption > 0
    && numericIntegratedConsumption <= 100_000
  const integratedResult = integratedProcessing.data
  function submitVerification(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setVerificationError('')
    setRecoveryError('')
    setRecoveredDemoPayload('')
    verify.reset()
    verify.mutate(envelope.trim())
  }
  function submitIntegratedDemo(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!integratedInputValid) return
    integratedProcessing.reset()
    verify.reset()
    setVerificationError('')
    setRecoveredDemoPayload('')
    setRecoveryError('')
    integratedProcessing.mutate(integratedConsumption)
  }
  function integratedProcessingError() {
    if (integratedProcessing.error instanceof ApiError
      && integratedProcessing.error.backendCode === 'DEMO_PREPARATION_REQUIRED') {
      return 'Demo environment preparation required. Ask an operator to provision the dedicated meter and keys.'
    }
    return integratedProcessing.error?.message
  }
  return (
    <div className="feature-page feature-energy">
      <header className="page-heading"><p className="eyebrow">Security</p><h1>Energy stream processing</h1><p className="muted">Review recorded server-side processing runs and the decorator chain reported by the backend.</p></header>
      <Card><CardHeader><CardTitle>Processing history</CardTitle></CardHeader><CardContent>
        {runs.isPending && <LoadingState label="Loading energy processing history" />}
        {runs.isError && <ErrorState title="Energy history unavailable" message={runs.error.message} onRetry={() => void runs.refetch()} />}
        {runs.data?.length === 0 && <EmptyState title="No processing runs recorded" message="The backend returned no energy processing history." />}
        {runs.data && runs.data.length > 0 && <>
          <div className="table-scroll"><table className="data-table"><thead><tr><th>Run</th><th>Meter</th><th>Status</th><th>Decorator chain</th><th>Bytes in / out</th><th>Duration</th><th>Executed</th></tr></thead><tbody>
            {runs.data.map((run) => <tr key={run.id}><td><code>{run.id}</code>{run.errorCode && <small className="table-secondary">Error: {run.errorCode}</small>}</td><td>{run.meterCode}</td><td><span className="status-pill" data-state={run.status.toLowerCase()}>{run.status}</span></td><td><ol className="chain-list">{chainLayers(run.decoratorChain).map((layer, index) => <li key={`${layer}-${index}`}>{layer.replaceAll('_', ' ')}</li>)}</ol></td><td>{run.inputBytes} / {run.outputBytes ?? '—'}</td><td>{run.durationMs === null ? '—' : `${run.durationMs} ms`}</td><td>{new Date(run.executedAt).toLocaleString()}</td></tr>)}
          </tbody></table></div>
          <div className="pagination"><Button variant="secondary" disabled={!offset} onClick={() => setOffset(Math.max(0, offset - pageSize))}>Previous</Button><span>Records {offset + 1}–{offset + runs.data.length}</span><Button variant="secondary" disabled={runs.data.length < pageSize} onClick={() => setOffset(offset + pageSize)}>Next</Button></div>
        </>}
      </CardContent></Card>
      <Card className="security-education"><CardHeader><CardTitle>Processing layers</CardTitle></CardHeader><CardContent>
        <ol className="education-chain"><li><strong>Authentication</strong><span>The backend authenticates meter requests using server-managed credentials and request proofs.</span></li><li><strong>Compression</strong><span>The backend compresses accepted payloads for processing.</span></li><li><strong>Encryption</strong><span>The backend protects payloads using server-managed encryption keys.</span></li></ol>
        <p className="muted">Meter HMAC credentials, AES keys, and signed meter submissions are never present in browser code. Verification is performed by the backend with server-managed keys; the service authorizes it for ADMIN and CITY_MANAGER roles.</p>
      </CardContent></Card>
      {canVerify && <Card><CardHeader><CardTitle>Verify protected payload</CardTitle></CardHeader><CardContent className="feature-stack">
        <p className="muted">Submit a Base64 protected envelope supplied by an authorized workflow. The backend verifies and decrypts it; no cryptographic key is sent to or used by this browser.</p>
        <form className="stack-form" onSubmit={submitVerification}>
          <Label htmlFor="energy-envelope">Protected envelope (Base64)</Label>
          <textarea id="energy-envelope" required rows={4} value={envelope} onChange={(event) => {
            setEnvelope(event.target.value)
            setRecoveredDemoPayload('')
            setRecoveryError('')
            verify.reset()
          }} />
          <Button disabled={verify.isPending}>Verify on backend</Button>
        </form>
        {verificationError && <p role="alert" className="form-error">{verificationError}</p>}
        {verify.data && <section className="verification-result" aria-live="polite"><h3>Encrypted envelope verified and decrypted</h3><Label htmlFor="verified-payload">Verified payload (Base64)</Label><textarea id="verified-payload" rows={4} readOnly value={verify.data.payloadBase64} /></section>}
        {recoveryError && <p role="alert" className="form-error">{recoveryError}</p>}
        {recoveredDemoPayload && <section className="verification-result" aria-live="polite">
          <h3>GZIP payload recovered and matched the submitted reading</h3>
          <Label htmlFor="recovered-demo-reading">Recovered original reading (JSON)</Label>
          <textarea id="recovered-demo-reading" rows={3} readOnly value={recoveredDemoPayload} />
        </section>}
      </CardContent></Card>}
      {canDemonstrate && integratedDemoAvailability.isError && <p className="notice-state" role="alert">Integrated demo readiness could not be confirmed.</p>}
      {canDemonstrate && integratedDemoAvailability.data?.enabled === false && <p className="notice-state" role="status">Integrated Meter Processing is disabled in this environment.</p>}
      {canDemonstrate && integratedDemoAvailability.data?.enabled && !integratedDemoAvailability.data.prepared && <p className="notice-state" role="status">Demo environment preparation required. The dedicated academic meter or its configured keys are unavailable.</p>}
      {canDemonstrate && integratedDemoAvailability.data?.enabled && integratedDemoAvailability.data.prepared && <Card>
        <CardHeader><CardTitle>Integrated Meter Processing</CardTitle></CardHeader>
        <CardContent className="feature-stack">
          <p className="muted">ADMIN-initiated sample ENERGY METER for illustrating a independent physical meter.</p>
          <form className="stack-form" onSubmit={submitIntegratedDemo}>
            <Label htmlFor="integrated-demo-consumption">Energy consumption (kWh)</Label>
            <Input
              id="integrated-demo-consumption"
              type="number"
              min="0.000001"
              max="100000"
              step="any"
              required
              value={integratedConsumption}
              aria-invalid={!integratedInputValid}
              aria-describedby="integrated-demo-consumption-hint"
              onChange={(event) => setIntegratedConsumption(event.target.value)}
            />
            <p id="integrated-demo-consumption-hint" className={integratedInputValid ? 'form-hint muted' : 'form-error'} role={integratedInputValid ? undefined : 'alert'}>
              {integratedInputValid ? 'Enter a finite value greater than 0 and no greater than 100,000 kWh.' : 'Enter a valid decimal value between 0 and 100,000 kWh.'}
            </p>
            <Button disabled={integratedProcessing.isPending || !integratedInputValid}>
              {integratedProcessing.isPending ? 'Processing demo meter reading…' : 'Process Demo Meter Reading'}
            </Button>
          </form>
          {integratedProcessing.isPending && <p role="status" aria-live="polite">Authenticating and processing the dedicated demo meter request…</p>}
          {integratedProcessing.isError && <p role="alert" className="form-error">{integratedProcessingError()}</p>}
          {integratedResult && !isIntegratedResultConfirmed(integratedResult) && <p role="alert" className="form-error">The backend response did not confirm successful authenticated processing and persistence.</p>}
          {integratedResult && isIntegratedResultConfirmed(integratedResult) && <section className="verification-result" aria-live="polite">
            <h3>Integrated processing result</h3>
            <dl className="detail-grid">
              <dt>Actual processing run ID</dt><dd><code>{integratedResult.runId}</code></dd>
              <dt>Backend status</dt><dd><span className="status-pill" data-state={integratedResult.status.toLowerCase()}>{integratedResult.status}</span></dd>
              <dt>Meter request authenticated</dt><dd>{integratedResult.meterAuthenticated ? 'Yes' : 'No'}</dd>
              <dt>Compression/encryption completed</dt><dd>{integratedResult.encryptionCompleted ? 'Yes' : 'No'}</dd>
              <dt>Processing record persisted</dt><dd>{integratedResult.processingRecordPersisted ? 'Yes' : 'No'}</dd>
              <dt>Input / encrypted output bytes</dt><dd>{integratedResult.inputBytes} / {integratedResult.encryptedOutputBytes}</dd>
            </dl>
            <div>
              <strong>Actual processing layers</strong>
              <ol className="chain-list">{integratedResult.decoratorLayers.map((layer, index) => <li key={`${layer}-${index}`}>{layer}</li>)}</ol>
            </div>
            <Label htmlFor="integrated-demo-envelope">Encrypted Base64 envelope (AES-GCM nonce is embedded)</Label>
            <textarea id="integrated-demo-envelope" rows={4} readOnly value={integratedResult.protectedPayloadBase64} />
            <Button type="button" variant="secondary" onClick={() => {
              setEnvelope(integratedResult.protectedPayloadBase64)
              setVerificationError('')
              setRecoveryError('')
              setRecoveredDemoPayload('')
              verify.reset()
            }}>Use in verification</Button>
          </section>}
        </CardContent>
      </Card>}
    </div>
  )
}
