import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ErrorState } from '@/components/common/error-state'
import { EmptyState } from '@/components/common/empty-state'
import { LoadingState } from '@/components/common/loading-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/features/auth/use-auth'
import { activatePolicyVersion, createPolicy, createPolicyVersion, deactivatePolicy, evaluatePolicy, getPolicy, getPolicyEvaluations, getPolicyVersions, listPolicies, validatePolicyVersion } from './policy-api'

const pageSize = 25
const formatDate = (value: string) => new Date(value).toLocaleString()

export function PoliciesPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const canManage = user?.roles.some((role) => role === 'ADMIN' || role === 'CITY_MANAGER') ?? false
  const isAdmin = user?.roles.includes('ADMIN') ?? false
  const [offset, setOffset] = useState(0)
  const [selectedId, setSelectedId] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [policyForm, setPolicyForm] = useState({ policyCode: '', name: '', domain: 'GENERAL', description: '' })
  const [dslExpression, setDslExpression] = useState('')
  const [contextText, setContextText] = useState('{\n  "HOUR": 12\n}')
  const policies = useQuery({ queryKey: ['policies', offset], queryFn: ({ signal }) => listPolicies(offset, pageSize, signal) })
  const detail = useQuery({ queryKey: ['policies', 'detail', selectedId], queryFn: ({ signal }) => getPolicy(selectedId, signal), enabled: Boolean(selectedId) })
  const versions = useQuery({ queryKey: ['policies', selectedId, 'versions'], queryFn: ({ signal }) => getPolicyVersions(selectedId, 0, pageSize, signal), enabled: Boolean(selectedId && canManage) })
  const evaluations = useQuery({ queryKey: ['policies', selectedId, 'evaluations'], queryFn: ({ signal }) => getPolicyEvaluations(selectedId, 0, pageSize, signal), enabled: Boolean(selectedId && canManage) })
  const refresh = async (message: string) => {
    setFormError('')
    setNotice(message)
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['policies'] }),
    ])
  }
  const addPolicy = useMutation({ mutationFn: createPolicy, onSuccess: async (created) => { setSelectedId(created.id); evaluation.reset(); setPolicyForm({ policyCode: '', name: '', domain: 'GENERAL', description: '' }); await refresh('Policy created.') }, onError: (error) => setFormError(error.message) })
  const addVersion = useMutation({ mutationFn: ({ id, expression }: { id: string; expression: string }) => createPolicyVersion(id, expression), onSuccess: async () => { await refresh('Immutable policy version created.') }, onError: (error) => setFormError(error.message) })
  const validate = useMutation({ mutationFn: ({ id, versionId }: { id: string; versionId: string }) => validatePolicyVersion(id, versionId), onSuccess: async (version) => { await refresh(version.valid ? `Version ${version.versionNo} validated.` : version.validationError || `Version ${version.versionNo} is invalid.`) }, onError: (error) => setFormError(error.message) })
  const activate = useMutation({ mutationFn: ({ id, versionId }: { id: string; versionId: string }) => activatePolicyVersion(id, versionId), onSuccess: async (version) => { await refresh(`Version ${version.versionNo} activated.`) }, onError: (error) => setFormError(error.message) })
  const deactivate = useMutation({ mutationFn: deactivatePolicy, onSuccess: async (policy) => { await refresh(`Policy ${policy.policyCode} deactivated.`) }, onError: (error) => setFormError(error.message) })
  const evaluation = useMutation({ mutationFn: ({ id, context }: { id: string; context: Record<string, unknown> }) => evaluatePolicy(id, context), onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: ['policies', selectedId, 'evaluations'] }) }, onError: (error) => setFormError(error.message) })

  function submitPolicy(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    addPolicy.mutate(policyForm)
  }

  function submitVersion(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedId) return
    addVersion.mutate({ id: selectedId, expression: dslExpression })
  }

  function submitEvaluation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedId) return
    let context: Record<string, unknown>
    try {
      const parsed: unknown = JSON.parse(contextText)
      if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) throw new Error('The context must be a JSON object.')
      context = parsed as Record<string, unknown>
    } catch (error) {
      setFormError(error instanceof Error ? error.message : 'Evaluation context must be valid JSON.')
      return
    }
    setFormError('')
    evaluation.mutate({ id: selectedId, context })
  }

  const policy = detail.data
  const isInactive = policy?.status === 'INACTIVE'
  return (
    <div className="feature-page feature-policies">
      <header className="page-heading"><p className="eyebrow">Governance</p><h1>City policy interpreter</h1><p className="muted">Manage policy definitions and evaluate a policy against a caller-provided context.</p></header>
      {formError && <p className="form-error" role="alert">{formError}</p>}{notice && <p className="notice-state" role="status">{notice}</p>}
      <div className="feature-columns">
        <div className="feature-stack">
          <Card><CardHeader><CardTitle>Policies</CardTitle></CardHeader><CardContent>
            {policies.isPending && <LoadingState label="Loading policies" />}{policies.isError && <ErrorState title="Policies unavailable" message={policies.error.message} onRetry={() => void policies.refetch()} />}{policies.data?.length === 0 && <EmptyState title="No policies on this page" message="The backend returned no policy records." />}
            {policies.data && policies.data.length > 0 && <>
              <ul className="record-list">{policies.data.map((item) => <li key={item.id}><button className={`record-select${selectedId === item.id ? ' record-select-active' : ''}`} onClick={() => { setSelectedId(item.id); evaluation.reset(); setFormError(''); setNotice(''); setDslExpression('') }}><strong>{item.policyCode} · {item.name}</strong><span className="record-meta">{item.domain} <span className="status-pill" data-state={item.status.toLowerCase()}>{item.status}</span></span><span>{item.description}</span></button></li>)}</ul>
              <div className="pagination"><Button variant="secondary" disabled={!offset} onClick={() => setOffset(Math.max(0, offset - pageSize))}>Previous</Button><span>Records {offset + 1}–{offset + policies.data.length}</span><Button variant="secondary" disabled={policies.data.length < pageSize} onClick={() => setOffset(offset + pageSize)}>Next</Button></div>
            </>}
          </CardContent></Card>
          {canManage && <Card><CardHeader><CardTitle>Create policy</CardTitle></CardHeader><CardContent><form className="stack-form" onSubmit={submitPolicy}>
            <Label htmlFor="policy-code">Policy code</Label><Input id="policy-code" required maxLength={50} pattern="[A-Z][A-Z0-9_]{2,49}" value={policyForm.policyCode} onChange={(event) => setPolicyForm({ ...policyForm, policyCode: event.target.value.toUpperCase() })} />
            <Label htmlFor="policy-name">Name</Label><Input id="policy-name" required maxLength={150} value={policyForm.name} onChange={(event) => setPolicyForm({ ...policyForm, name: event.target.value })} />
            <label>Domain<select value={policyForm.domain} onChange={(event) => setPolicyForm({ ...policyForm, domain: event.target.value })}>{['NOISE', 'PARKING', 'GENERAL'].map((domain) => <option key={domain}>{domain}</option>)}</select></label>
            <label>Description<textarea maxLength={4000} rows={3} value={policyForm.description} onChange={(event) => setPolicyForm({ ...policyForm, description: event.target.value })} /></label>
            <Button disabled={addPolicy.isPending}>Create policy</Button>
          </form></CardContent></Card>}
        </div>
        {selectedId ? <Card><CardHeader><CardTitle>{policy?.name ?? 'Policy details'}</CardTitle></CardHeader><CardContent className="feature-stack">
          {detail.isPending && <LoadingState label="Loading policy details" />}{detail.isError && <ErrorState title="Policy unavailable" message={detail.error.message} onRetry={() => void detail.refetch()} />}
          {policy && <>
            <p className="eyebrow policy-context">{policy.policyCode} · {policy.domain} <span className="status-pill" data-state={policy.status.toLowerCase()}>{policy.status}</span></p><p>{policy.description}</p>
            <dl className="detail-grid"><dt>Created</dt><dd>{formatDate(policy.createdAt)}</dd><dt>Updated</dt><dd>{formatDate(policy.updatedAt)}</dd><dt>Created by</dt><dd>{policy.createdBy}</dd></dl>
            {isAdmin && policy.status !== 'INACTIVE' && <Button variant="destructive" disabled={deactivate.isPending} onClick={() => { if (window.confirm(`Deactivate policy ${policy.policyCode}?`)) deactivate.mutate(policy.id) }}>Deactivate policy</Button>}
            {canManage && <>
              {isInactive && <p role="status">This policy is deactivated. Its versions and history remain available, but it cannot be modified or reactivated through this workflow.</p>}
              <form className="stack-form" onSubmit={submitVersion}><h3>Create immutable version</h3><Label htmlFor="policy-dsl">DSL expression</Label><textarea id="policy-dsl" required rows={5} value={dslExpression} onChange={(event) => setDslExpression(event.target.value)} placeholder="Example: HOUR >= 8 AND HOUR < 18" disabled={isInactive} /><Button disabled={addVersion.isPending || isInactive}>Create version</Button></form>
              <section><h3>Version history</h3>{versions.isPending && <LoadingState label="Loading versions" />}{versions.isError && <ErrorState title="Version history unavailable" message={versions.error.message} onRetry={() => void versions.refetch()} />}{versions.data?.length === 0 && <EmptyState title="No versions yet" message="Create a DSL version to validate and activate it." />}{versions.data && versions.data.length > 0 && <ul className="event-list">{versions.data.map((version) => <li key={version.id}><strong>Version {version.versionNo} · <span className="status-pill" data-state={version.valid ? 'valid' : 'invalid'}>{version.valid ? 'Valid' : 'Not validated'}</span>{version.active && <> · <span className="status-pill" data-state="active">Active</span></>}</strong><code className="dsl-code">{version.dslExpression}</code>{version.validationError && <span role="alert">{version.validationError}</span>}<span>Created {formatDate(version.createdAt)}</span><div className="inline-actions"><Button variant="secondary" size="sm" disabled={validate.isPending || isInactive} onClick={() => validate.mutate({ id: policy.id, versionId: version.id })}>Validate</Button><Button variant="secondary" size="sm" disabled={!version.valid || activate.isPending || version.active || isInactive} onClick={() => activate.mutate({ id: policy.id, versionId: version.id })}>Activate</Button></div></li>)}</ul>}</section>
            </>}
            {policy.status === 'ACTIVE'
              ? <form className="stack-form" onSubmit={submitEvaluation}><h3>Evaluate policy</h3><p className="muted">Context variables available in the backend registry: <code>NOISE_DB</code>, <code>HOUR</code>, <code>PARKING_MINUTES</code>, <code>ZONE</code>, <code>IS_WEEKEND</code>. Domain restrictions are enforced by the service.</p><Label htmlFor="evaluation-context">Evaluation context (JSON object)</Label><textarea id="evaluation-context" rows={6} value={contextText} onChange={(event) => setContextText(event.target.value)} /><Button disabled={evaluation.isPending}>Evaluate</Button></form>
              : <EmptyState title="Evaluation unavailable" message={isInactive ? 'Evaluation is unavailable while this policy is deactivated.' : 'The backend only evaluates active policies. Activate a valid version first.'} />}
            {evaluation.data && <section className={`evaluation-result evaluation-${evaluation.data.status.toLowerCase()}`} aria-live="polite"><h3>Evaluation result: <span className="status-pill" data-state={evaluation.data.status.toLowerCase()}>{evaluation.data.status}</span></h3>{evaluation.data.result !== null && <p>Result: <strong>{String(evaluation.data.result).toUpperCase()}</strong></p>}{evaluation.data.errorCode && <p>Error code: {evaluation.data.errorCode}</p>}<p>Version {evaluation.data.versionNo} · {evaluation.data.durationMs} ms · {formatDate(evaluation.data.evaluatedAt)}</p>{evaluation.data.explanationJson && <details><summary>Evaluation explanation</summary><pre>{evaluation.data.explanationJson}</pre></details>}</section>}
            {canManage && <section><h3>Evaluation history</h3>{evaluations.isPending && <LoadingState label="Loading evaluation history" />}{evaluations.isError && <ErrorState title="Evaluation history unavailable" message={evaluations.error.message} onRetry={() => void evaluations.refetch()} />}{evaluations.data?.length === 0 && <EmptyState title="No evaluations recorded" message="Evaluation results will be listed here." />}{evaluations.data && evaluations.data.length > 0 && <ul className="event-list">{evaluations.data.map((item) => <li key={item.id}><strong><span className="status-pill" data-state={item.status.toLowerCase()}>{item.status}</span>{item.result !== null && ` · ${String(item.result).toUpperCase()}`}</strong><span>Version {item.versionNo} · {item.durationMs} ms · {formatDate(item.evaluatedAt)}</span>{item.errorCode && <span>Error: {item.errorCode}</span>}</li>)}</ul>}</section>}
            {!canManage && <p className="muted">The backend restricts version and evaluation history access to administrators and city managers.</p>}
          </>}
        </CardContent></Card> : <EmptyState title="Select a policy" message="Choose a policy to view its versions, run an evaluation, or review its history." />}
      </div>
    </div>
  )
}
