import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ErrorState } from '@/components/common/error-state'
import { EmptyState } from '@/components/common/empty-state'
import { LoadingState } from '@/components/common/loading-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/api-error'
import type { SmartCityRole } from '@/lib/api/contracts'
import { useAuth } from '@/features/auth/use-auth'
import { dashboardQueryKey } from '@/features/dashboard/dashboard-api'
import { createZone, getZone, getZoneCommands, getZonePath, getZoneTree, getZoneUsage, listZoneParents, listZones, runZonePowerCommand, updateZone, type Zone, type ZoneTree } from './zone-api'
import { createZonePayload, filterEligibleParents, formatZoneCode, getParentType, isCreateZoneValid, resetParentForType, type CreateZoneForm, type ZoneType } from './zone-form'
import { parentLabel } from './zone-parent'

const pageSize = 25
const formatDate = (value: string) => new Date(value).toLocaleString()

function TreeNode({ node }: { node: ZoneTree }) {
  return <li><span>{node.name} <small>({node.type} · {node.code})</small></span>{node.children.length > 0 && <ul>{node.children.map((child) => <TreeNode key={child.id} node={child} />)}</ul>}</li>
}

export function ZonesPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [offset, setOffset] = useState(0)
  const [selectedId, setSelectedId] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [newZone, setNewZone] = useState<CreateZoneForm>({ type: 'CITY', code: '', name: '', parentId: '' })
  const [parentSearch, setParentSearch] = useState('')
  const [parentOffset, setParentOffset] = useState(0)
  const [editParentSearch, setEditParentSearch] = useState('')
  const [editParentOffset, setEditParentOffset] = useState(0)
  const [edit, setEdit] = useState({ code: '', name: '', parentId: '' })
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [usageRange, setUsageRange] = useState<{ from: string; to: string }>()
  const canManage = user?.roles.includes('ADMIN') ?? false
  const canOperate = user?.roles.some((role: SmartCityRole) => role === 'ADMIN' || role === 'CITY_MANAGER') ?? false
  const zones = useQuery({ queryKey: ['zones', offset], queryFn: ({ signal }) => listZones(offset, pageSize, signal) })
  const parentType = getParentType(newZone.type)
  const formattedCode = formatZoneCode(newZone.type, newZone.code)
  const parentOptions = useQuery({
    queryKey: ['zones', 'parents', parentType, parentSearch, parentOffset],
    queryFn: ({ signal }) => listZoneParents(parentType!, parentSearch, parentOffset, pageSize, signal),
    enabled: canManage && parentType !== null,
  })
  const selected = zones.data?.find((zone) => zone.id === selectedId)
  const editParentType = selected ? getParentType(selected.type as ZoneType) : null
  const selectedParent = useQuery({
    queryKey: ['zones', selected?.parentId, 'detail'],
    queryFn: ({ signal }) => getZone(selected!.parentId!, signal),
    enabled: Boolean(selected?.parentId),
  })
  const path = useQuery({
    queryKey: ['zones', selectedId, 'path'],
    queryFn: ({ signal }) => getZonePath(selectedId, signal),
    enabled: Boolean(selectedId),
  })
  const editParentOptions = useQuery({
    queryKey: ['zones', 'parents', 'edit', editParentType, editParentSearch, editParentOffset],
    queryFn: ({ signal }) => listZoneParents(editParentType!, editParentSearch, editParentOffset, pageSize, signal),
    enabled: canManage && editParentType !== null,
  })
  const tree = useQuery({
    queryKey: ['zones', selectedId, 'tree'],
    queryFn: ({ signal }) => getZoneTree(selectedId, signal),
    enabled: Boolean(selectedId),
  })
  const commands = useQuery({
    queryKey: ['zones', selectedId, 'commands'],
    queryFn: ({ signal }) => getZoneCommands(selectedId, 0, pageSize, signal),
    enabled: Boolean(selectedId),
  })
  const usage = useQuery({
    queryKey: ['zones', selectedId, 'usage', usageRange],
    queryFn: ({ signal }) => getZoneUsage(selectedId, usageRange!.from, usageRange!.to, signal),
    enabled: Boolean(selectedId && usageRange),
  })
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['zones'] })
  const create = useMutation({ mutationFn: createZone, onSuccess: (zone) => { setSelectedId(zone.id); setNewZone({ type: 'CITY', code: '', name: '', parentId: '' }); setParentSearch(''); setParentOffset(0); setNotice('Zone created.'); setFormError(''); void invalidate(); void queryClient.invalidateQueries({ queryKey: dashboardQueryKey }) }, onError: (error) => setFormError(error instanceof ApiError && error.backendCode === 'ZONE_CODE_CONFLICT' ? 'This zone code already exists. Please choose another code.' : error.message) })
  const save = useMutation({ mutationFn: ({ id, body }: { id: string; body: { code: string; name: string; parentId: string | null } }) => updateZone(id, body), onSuccess: () => { setNotice('Zone updated.'); setFormError(''); return invalidate() }, onError: (error) => setFormError(error.message) })
  const power = useMutation({ mutationFn: ({ id, command }: { id: string; command: 'shutdown' | 'restore' }) => runZonePowerCommand(id, command), onSuccess: (result) => { setNotice(result.message); setFormError(''); void invalidate(); void queryClient.invalidateQueries({ queryKey: ['zones', result.targetZoneId] }) }, onError: (error) => setFormError(error.message) })
  const editParentCandidates: Zone[] = editParentOptions.data
    ? filterEligibleParents(editParentOptions.data, selected?.type as ZoneType)
    : []
  const currentParentIsEligible = selectedParent.data
    && selectedParent.data.status === 'ACTIVE'
    && selectedParent.data.type === editParentType
  const editParentIsValid = editParentType === null
    ? edit.parentId === ''
    : editParentCandidates.some((zone) => zone.id === edit.parentId)
      || Boolean(currentParentIsEligible && selectedParent.data?.id === edit.parentId)
  function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setNotice('')
    if (!isCreateZoneValid(newZone)) {
      setFormError(parentType === null ? 'CITY zones cannot have a parent.' : `Select an active ${parentType} parent before creating this zone.`)
      return
    }
    create.mutate(createZonePayload(newZone))
  }

  function changeCreateType(type: ZoneType) {
    setNewZone(resetParentForType(newZone, type))
    setParentSearch('')
    setParentOffset(0)
    setFormError('')
  }

  function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selected) return
    if (editParentType !== null && !editParentIsValid) {
      setFormError(`Select an active ${editParentType} parent before saving this zone.`)
      return
    }
    save.mutate({ id: selected.id, body: { ...edit, parentId: edit.parentId || null } })
  }

  function requestPower(command: 'shutdown' | 'restore') {
    if (!selected || !window.confirm(`Submit the ${command} command for ${selected.name}? This operation is recorded by the backend.`)) return
    setFormError('')
    setNotice('')
    power.mutate({ id: selected.id, command })
  }

  function submitUsage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!from || !to) return
    const start = new Date(from)
    const end = new Date(to)
    if (Number.isNaN(start.valueOf()) || Number.isNaN(end.valueOf()) || start >= end) {
      setFormError('Choose a valid reporting range where the start precedes the end.')
      return
    }
    setUsageRange({ from: start.toISOString(), to: end.toISOString() })
    setFormError('')
  }

  return (
    <div className="feature-page feature-zones">
      <header className="page-heading"><p className="eyebrow">Operations</p><h1>Smart zones</h1><p className="muted">Browse the zone hierarchy, review operations, and report metered usage.</p></header>
      {formError && <p className="form-error" role="alert">{formError}</p>}
      {notice && <p className="notice-state" role="status">{notice}</p>}
      <div className="feature-columns">
        <Card>
          <CardHeader><CardTitle>Zones</CardTitle></CardHeader>
          <CardContent>
            {zones.isPending && <LoadingState label="Loading zones" />}
            {zones.isError && <ErrorState title="Zones unavailable" message={zones.error.message} onRetry={() => void zones.refetch()} />}
            {zones.data?.length === 0 && <EmptyState title="No zones on this page" message="There are no zone records to display." />}
            {zones.data && zones.data.length > 0 && (
              <>
                <ul className="record-list">{zones.data.map((zone) => (
                  <li key={zone.id}>
                    <button className={`record-select${selectedId === zone.id ? ' record-select-active' : ''}`} onClick={() => { setSelectedId(zone.id); setEdit({ code: zone.code, name: zone.name, parentId: zone.parentId ?? '' }); setEditParentSearch(''); setEditParentOffset(0); setUsageRange(undefined) }}>
                      <strong>{zone.name}</strong><span className="record-meta">{zone.type} · {zone.code}</span><span className="status-pill" data-state={zone.status.toLowerCase()}>{zone.status}</span>
                    </button>
                  </li>
                ))}</ul>
                <div className="pagination">
                  <Button variant="secondary" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - pageSize))}>Previous</Button>
                  <span>Records {offset + 1}–{offset + zones.data.length}</span>
                  <Button variant="secondary" disabled={zones.data.length < pageSize} onClick={() => setOffset(offset + pageSize)}>Next</Button>
                </div>
              </>
            )}
          </CardContent>
        </Card>
        <div className="feature-stack">
          {canManage && <Card><CardHeader><CardTitle>Create zone</CardTitle></CardHeader><CardContent><form className="stack-form" onSubmit={submitCreate}>
            <label htmlFor="zone-type">Type<select id="zone-type" value={newZone.type} onChange={(event) => changeCreateType(event.target.value as ZoneType)}>{['CITY', 'DISTRICT', 'STREET', 'BUILDING'].map((type) => <option key={type}>{type}</option>)}</select></label>
            <Label htmlFor="zone-code">Code</Label><Input id="zone-code" required maxLength={40} value={newZone.code} onChange={(event) => setNewZone({ ...newZone, code: event.target.value })} />
            {formattedCode.code ? <p className="muted" aria-live="polite">Generated code: <strong>{formattedCode.code}</strong></p> : newZone.code && <p className="form-error" role="alert">{formattedCode.error}</p>}
            <Label htmlFor="zone-name">Name</Label><Input id="zone-name" required maxLength={150} value={newZone.name} onChange={(event) => setNewZone({ ...newZone, name: event.target.value })} />
            {parentType === null ? <p className="muted">CITY zones are root zones and do not have a parent.</p> : <>
              <Label htmlFor="zone-parent-search">Search active {parentType} parents</Label>
              <Input id="zone-parent-search" value={parentSearch} placeholder="Search by name or code" onChange={(event) => { setParentSearch(event.target.value); setParentOffset(0) }} />
              <Label htmlFor="zone-parent">Parent zone <span aria-hidden="true">*</span></Label>
              <select id="zone-parent" required value={newZone.parentId} onChange={(event) => setNewZone({ ...newZone, parentId: event.target.value })} disabled={parentOptions.isPending || parentOptions.isError}>
                <option value="">Select an active {parentType}</option>
                {parentOptions.data && filterEligibleParents(parentOptions.data, newZone.type).map((zone) => <option key={zone.id} value={zone.id}>{zone.name} · {zone.code}</option>)}
              </select>
              {parentOptions.isPending && <LoadingState label={`Loading active ${parentType} parents`} />}
              {parentOptions.isError && <ErrorState title="Parent zones unavailable" message="Parent choices could not be loaded." onRetry={() => void parentOptions.refetch()} />}
              {parentOptions.data?.length === 0 && <EmptyState title={`No active ${parentType} parents found`} message="Create or activate the required parent zone before continuing." />}
              {parentOptions.data && parentOptions.data.length > 0 && <div className="pagination"><Button type="button" variant="secondary" disabled={parentOffset === 0} onClick={() => setParentOffset(Math.max(0, parentOffset - pageSize))}>Previous parents</Button><span>Parent results {parentOffset + 1}–{parentOffset + parentOptions.data.length}</span><Button type="button" variant="secondary" disabled={parentOptions.data.length < pageSize} onClick={() => setParentOffset(parentOffset + pageSize)}>Next parents</Button></div>}
            </>}
            <Button disabled={create.isPending || !isCreateZoneValid(newZone)}>Create zone</Button>
          </form></CardContent></Card>}
          {selected ? <Card>
            <CardHeader><CardTitle>{selected.name}</CardTitle></CardHeader>
            <CardContent className="feature-stack">
              <dl className="detail-grid"><dt>Type</dt><dd>{selected.type}</dd><dt>Code</dt><dd>{selected.code}</dd><dt>Status</dt><dd><span className="status-pill" data-state={selected.status.toLowerCase()}>{selected.status}</span></dd><dt>Parent zone</dt><dd>{selected.parentId === null ? 'Root zone — no parent' : selectedParent.isPending ? 'Loading parent zone' : parentLabel(selectedParent.data)}</dd><dt>Created</dt><dd>{formatDate(selected.createdAt)}</dd></dl>
              <section><h3>Hierarchy path</h3>{path.isPending && <LoadingState label="Loading hierarchy path" />}{path.isError && <ErrorState title="Hierarchy path unavailable" message={path.error.message} onRetry={() => void path.refetch()} />}{path.data && <p className="muted">{path.data.map((zone) => `${zone.name} (${zone.type} · ${zone.code})`).join(' → ')}</p>}</section>
              {canManage && <form className="stack-form" onSubmit={submitEdit}>
                <h3>Edit zone</h3><Label htmlFor="edit-zone-code">Code</Label><Input id="edit-zone-code" required maxLength={40} value={edit.code} onChange={(event) => setEdit({ ...edit, code: event.target.value })} />
                <Label htmlFor="edit-zone-name">Name</Label><Input id="edit-zone-name" required maxLength={150} value={edit.name} onChange={(event) => setEdit({ ...edit, name: event.target.value })} />
                {editParentType === null ? <p className="muted">CITY zones are root zones and do not have a parent.</p> : <><Label htmlFor="edit-zone-parent-search">Search active {editParentType} parents</Label><Input id="edit-zone-parent-search" value={editParentSearch} placeholder="Search by name or code" onChange={(event) => { setEditParentSearch(event.target.value); setEditParentOffset(0) }} /><Label htmlFor="edit-zone-parent">Parent zone <span aria-hidden="true">*</span></Label><select id="edit-zone-parent" value={edit.parentId} onChange={(event) => setEdit({ ...edit, parentId: event.target.value })} disabled={editParentOptions.isPending || editParentOptions.isError}><option value="">Select an active {editParentType}</option>{currentParentIsEligible && edit.parentId === selectedParent.data?.id && <option value={selectedParent.data.id}>{parentLabel(selectedParent.data)}</option>}{editParentCandidates.filter((zone) => zone.id !== selectedParent.data?.id).map((zone) => <option key={zone.id} value={zone.id}>{parentLabel(zone)}</option>)}</select>{editParentOptions.isError && <ErrorState title="Parent zones unavailable" message="The current parent has not been changed. Retry to choose another parent." onRetry={() => void editParentOptions.refetch()} />}{editParentOptions.data && editParentOptions.data.length > 0 && <div className="pagination"><Button type="button" variant="secondary" disabled={editParentOffset === 0} onClick={() => setEditParentOffset(Math.max(0, editParentOffset - pageSize))}>Previous parents</Button><span>Parent results {editParentOffset + 1}–{editParentOffset + editParentOptions.data.length}</span><Button type="button" variant="secondary" disabled={editParentOptions.data.length < pageSize} onClick={() => setEditParentOffset(editParentOffset + pageSize)}>Next parents</Button></div>}</>}
                <Button variant="secondary" disabled={save.isPending || !editParentIsValid}>Save changes</Button>
              </form>}
              {canOperate && <div className="inline-actions"><Button variant="secondary" disabled={power.isPending} onClick={() => requestPower('shutdown')}>Simulate shutdown</Button><Button variant="secondary" disabled={power.isPending} onClick={() => requestPower('restore')}>Restore power</Button></div>}
              <section><h3>Hierarchy</h3>{tree.isPending && <LoadingState label="Loading zone tree" />}{tree.isError && <ErrorState title="Hierarchy unavailable" message={tree.error.message} onRetry={() => void tree.refetch()} />}{tree.data && <ul className="tree-list"><TreeNode node={tree.data} /></ul>}</section>
              <section><h3>Energy usage report</h3><form className="range-form" onSubmit={submitUsage}><label>From<Input type="datetime-local" value={from} onChange={(event) => setFrom(event.target.value)} required /></label><label>To<Input type="datetime-local" value={to} onChange={(event) => setTo(event.target.value)} required /></label><Button variant="secondary">Load report</Button></form>
                {usage.isPending && usageRange && <LoadingState label="Loading energy usage report" />}
                {usage.isError && <ErrorState title="Usage report unavailable" message={usage.error.message} onRetry={() => void usage.refetch()} />}
                {usage.data && <dl className="detail-grid"><dt>Total kWh</dt><dd>{usage.data.totalKwh}</dd><dt>Readings</dt><dd>{usage.data.readingCount}</dd><dt>Meters</dt><dd>{usage.data.meterCount}</dd><dt>Buildings with readings</dt><dd>{usage.data.buildingsWithReadings}</dd><dt>Buildings without readings</dt><dd>{usage.data.buildingsWithoutReadings}</dd><dt>Inclusion rule</dt><dd>{usage.data.inclusionRule}</dd></dl>}
              </section>
              <section><h3>Command history</h3>{commands.isPending && <LoadingState label="Loading command history" />}{commands.isError && <ErrorState title="Command history unavailable" message={commands.error.message} onRetry={() => void commands.refetch()} />}{commands.data?.length === 0 && <EmptyState title="No commands recorded" message="No zone commands are available for this zone." />}{commands.data && commands.data.length > 0 && <ul className="event-list">{commands.data.map((item) => <li key={item.id}><strong>{item.command} · <span className="status-pill" data-state={item.status.toLowerCase()}>{item.status}</span></strong><span>{formatDate(item.issuedAt)} · {item.message}</span><span>{item.affectedBuildings} buildings affected</span></li>)}</ul>}</section>
            </CardContent>
          </Card> : <EmptyState title="Select a zone" message="Choose a zone to inspect its hierarchy, report, and command history." />}
        </div>
      </div>
    </div>
  )
}
