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
import { assignComplaintToSelf, escalateComplaint, getComplaintHistory, getComplaint, listAllComplaints, listComplaintQueue, listMyComplaints, resolveComplaint, startComplaint, submitComplaint, type Complaint } from './complaint-api'
import { complaintActionPolicy } from './complaint-action-policy'
import { listComplaintLocations } from './complaint-location-api'
import { ApiError } from '@/lib/api/api-error'
import { listManagedUsers } from '@/features/users/user-api'

const pageSize = 25
const formatDate = (value: string) => new Date(value).toLocaleString()

function displayHandlerLevel(handlerLevel: string) {
  return handlerLevel
    .toLowerCase()
    .split('_')
    .map((word) => `${word.charAt(0).toUpperCase()}${word.slice(1)}`)
    .join(' ')
}

function displayZone(complaint: Complaint) {
  if (complaint.zoneName?.trim() && complaint.zoneCode?.trim()) {
    return `${complaint.zoneName} (${complaint.zoneCode})`
  }
  return 'Zone unavailable'
}

function displayAssignee(complaint: Complaint) {
  if (complaint.assignedToUserId === null) {
    return 'Unassigned'
  }
  return complaint.assignedUserDisplayName?.trim() || 'Assigned user unavailable'
}

export function ComplaintsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isResident = user?.roles.includes('RESIDENT') ?? false
  const isAdmin = user?.roles.includes('ADMIN') ?? false
  const staffRoles = user?.roles.filter((role) => role !== 'RESIDENT') ?? []
  const [offset, setOffset] = useState(0)
  const [selectedId, setSelectedId] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [submission, setSubmission] = useState({ title: '', description: '', severity: 'MEDIUM', zoneId: '' })
  const [locationSearch, setLocationSearch] = useState('')
  const [locationOffset, setLocationOffset] = useState(0)
  const [assignmentResetCount, setAssignmentResetCount] = useState(0)
  const [escalationReason, setEscalationReason] = useState('')
  const [resolutionNote, setResolutionNote] = useState('')
  const pageQuery = useQuery({
    queryKey: ['complaints', isResident, staffRoles, offset],
    queryFn: ({ signal }) => isResident
      ? listMyComplaints(offset, pageSize, signal)
      : isAdmin
        ? listAllComplaints(offset, pageSize, signal)
        : listComplaintQueue(offset, pageSize, signal),
  })
  const selected = useQuery({
    queryKey: ['complaints', 'detail', selectedId],
    queryFn: async ({ signal }) => {
      try {
        return await getComplaint(selectedId, signal)
      } catch (error) {
        if (error instanceof ApiError && (error.status === 403 || error.status === 404)) {
          queryClient.removeQueries({ queryKey: ['complaints', 'detail', selectedId] })
          queryClient.removeQueries({ queryKey: ['complaints', selectedId, 'history'] })
          setSelectedId('')
        }
        throw error
      }
    },
    enabled: Boolean(selectedId),
  })
  const locations = useQuery({
    queryKey: ['complaint-locations', locationSearch, locationOffset],
    queryFn: ({ signal }) => listComplaintLocations(locationSearch, locationOffset, pageSize, signal),
    enabled: isResident,
  })
  const history = useQuery({
    queryKey: ['complaints', selectedId, 'history'],
    queryFn: ({ signal }) => getComplaintHistory(selectedId, 0, pageSize, signal),
    enabled: Boolean(selectedId),
  })
  const refresh = async (complaint?: Complaint) => {
    setNotice(complaint ? `Complaint ${complaint.referenceNo} updated.` : 'Complaint submitted.')
    setFormError('')
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['complaints'] }),
    ])
  }
  const submit = useMutation({ mutationFn: submitComplaint, onSuccess: async (complaint) => { setSubmission({ title: '', description: '', severity: 'MEDIUM', zoneId: '' }); setSelectedId(complaint.id); await refresh() }, onError: (error) => setFormError(error.message) })
  const assign = useMutation({
    mutationFn: ({ id, assigneeId }: { id: string; assigneeId: string }) => assignComplaintToSelf(id, assigneeId),
    onSuccess: async (updatedComplaint) => {
      setAssignmentResetCount((count) => count + 1)
      await refresh(updatedComplaint)
    },
    onError: (error) => setFormError(error.message),
  })
  const start = useMutation({ mutationFn: startComplaint, onSuccess: refresh, onError: (error) => setFormError(error.message) })
  const escalate = useMutation({
    mutationFn: ({ id, level, reason }: { id: string; level: string; reason: string }) => escalateComplaint(id, level, reason),
    onSuccess: async (updatedComplaint) => {
      queryClient.removeQueries({ queryKey: ['complaints', 'detail', updatedComplaint.id] })
      queryClient.removeQueries({ queryKey: ['complaints', updatedComplaint.id, 'history'] })
      setSelectedId('')
      setEscalationReason('')
      setFormError('')
      setNotice(`Complaint ${updatedComplaint.referenceNo} escalated successfully.`)
      await queryClient.invalidateQueries({ queryKey: ['complaints'] })
    },
    onError: (error) => setFormError(error.message),
  })
  const resolve = useMutation({ mutationFn: ({ id, note }: { id: string; note: string }) => resolveComplaint(id, note), onSuccess: refresh, onError: (error) => setFormError(error.message) })
  const complaint = selected.isError ? undefined : selected.data
  const actionPolicy = complaintActionPolicy(complaint, user)

  function submitNewComplaint(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError('')
    if (!submission.zoneId) {
      setFormError('Select an active location before submitting a complaint.')
      return
    }
    submit.mutate(submission)
  }

  function submitEscalation(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!complaint) return
    escalate.mutate({ id: complaint.id, level: complaint.currentHandlerLevel, reason: escalationReason })
  }

  function submitResolution(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!complaint) return
    resolve.mutate({ id: complaint.id, note: resolutionNote })
  }

  return (
    <div className="feature-page feature-complaints">
      <header className="page-heading"><p className="eyebrow">Community</p><h1>Citizen complaints</h1><p className="muted">Residents can track their submissions; authorized staff can work their assigned queue.</p></header>
      {formError && <p className="form-error" role="alert">{formError}</p>}
      {notice && <p className="notice-state" role="status">{notice}</p>}
      <div className="feature-columns">
        <div className="feature-stack">
          {isResident && <Card><CardHeader><CardTitle>Submit a complaint</CardTitle></CardHeader><CardContent><form className="stack-form" onSubmit={submitNewComplaint}>
            <Label htmlFor="complaint-title">Title</Label><Input id="complaint-title" required maxLength={180} value={submission.title} onChange={(event) => setSubmission({ ...submission, title: event.target.value })} />
            <label>Description<textarea required maxLength={4000} rows={4} value={submission.description} onChange={(event) => setSubmission({ ...submission, description: event.target.value })} /></label>
            <label>Severity<select value={submission.severity} onChange={(event) => setSubmission({ ...submission, severity: event.target.value })}>{['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'].map((severity) => <option key={severity}>{severity}</option>)}</select></label>
            <Label htmlFor="complaint-location-search">Find a location</Label><Input id="complaint-location-search" value={locationSearch} onChange={(event) => { setLocationSearch(event.target.value); setLocationOffset(0) }} placeholder="Search by location name or code" />
            {locations.isPending && <LoadingState label="Loading active locations" />}
            {locations.isError && <ErrorState title="Locations unavailable" message={locations.error.message} onRetry={() => void locations.refetch()} />}
            {locations.data?.length === 0 && <EmptyState title="No active locations found" message="Try a different name or code. A location is required before you can submit." />}
            {locations.data && locations.data.length > 0 && <><Label htmlFor="complaint-location">Complaint location</Label><select id="complaint-location" required value={submission.zoneId} onChange={(event) => setSubmission({ ...submission, zoneId: event.target.value })}><option value="">Select an active location</option>{locations.data.map((location) => <option key={location.id} value={location.id}>{location.name} ({location.type} · {location.code})</option>)}</select><div className="pagination"><Button type="button" variant="secondary" disabled={locationOffset === 0} onClick={() => setLocationOffset(Math.max(0, locationOffset - pageSize))}>Previous locations</Button><Button type="button" variant="secondary" disabled={locations.data.length < pageSize} onClick={() => setLocationOffset(locationOffset + pageSize)}>More locations</Button></div></>}
            <p className="muted form-hint">Only active locations are shown. The backend verifies the selected location before accepting the complaint.</p>
            <Button disabled={submit.isPending || !submission.zoneId}>Submit complaint</Button>
          </form></CardContent></Card>}
          <Card><CardHeader><CardTitle>{isResident ? 'My complaints' : isAdmin ? 'Complaint register' : 'Staff queue'}</CardTitle></CardHeader><CardContent>
            {pageQuery.isPending && <LoadingState label="Loading complaints" />}
            {pageQuery.isError && <ErrorState title="Complaints unavailable" message={pageQuery.error.message} onRetry={() => void pageQuery.refetch()} />}
            {pageQuery.data?.length === 0 && <EmptyState title="No complaints to show" message={isResident ? 'Your submitted complaints will appear here.' : 'There are no complaints in this view.'} />}
            {pageQuery.data && pageQuery.data.length > 0 && <>
              <ul className="record-list">{pageQuery.data.map((item) => <li key={item.id}><button className={`record-select${selectedId === item.id ? ' record-select-active' : ''}`} onClick={() => { setSelectedId(item.id); setFormError(''); setNotice('') }}>
                <strong>{item.referenceNo} · {item.title}</strong><span className="complaint-record-states"><span className="status-pill" data-state={item.status.toLowerCase()}>{item.status}</span><span className="status-pill status-pill-severity" data-state={item.severity.toLowerCase()}>{item.severity}</span><span className="status-pill status-pill-neutral">{item.currentHandlerLevel}</span></span><span>Updated {formatDate(item.updatedAt)}</span>
              </button></li>)}</ul>
              <div className="pagination"><Button variant="secondary" disabled={offset === 0} onClick={() => setOffset(Math.max(0, offset - pageSize))}>Previous</Button><span>Records {offset + 1}–{offset + pageQuery.data.length}</span><Button variant="secondary" disabled={pageQuery.data.length < pageSize} onClick={() => setOffset(offset + pageSize)}>Next</Button></div>
            </>}
          </CardContent></Card>
        </div>
        {selectedId ? <Card><CardHeader><CardTitle>Complaint details</CardTitle></CardHeader><CardContent className="feature-stack">
          {selected.isPending && <LoadingState label="Loading complaint" />}
          {selected.isError && <ErrorState title="Complaint unavailable" message={selected.error.message} onRetry={() => void selected.refetch()} />}
          {complaint && <>
            <p className="eyebrow">{complaint.referenceNo}</p><h2 className="record-title">{complaint.title}</h2><p>{complaint.description}</p>
            <dl className="detail-grid"><dt>Status</dt><dd><span className="status-pill" data-state={complaint.status.toLowerCase()}>{complaint.status}</span></dd><dt>Severity</dt><dd><span className="status-pill status-pill-severity" data-state={complaint.severity.toLowerCase()}>{complaint.severity}</span></dd><dt>Zone</dt><dd>{displayZone(complaint)}</dd><dt>Handler</dt><dd>{displayHandlerLevel(complaint.currentHandlerLevel)}</dd><dt>Assigned user</dt><dd>{displayAssignee(complaint)}</dd><dt>Submitted</dt><dd>{formatDate(complaint.createdAt)}</dd>{complaint.resolvedAt && <><dt>Resolved</dt><dd>{formatDate(complaint.resolvedAt)}</dd></>}</dl>
            {isResident && complaint.residentId !== user?.id && <p className="muted">Ownership is verified by the backend.</p>}
            {(actionPolicy.canAssign || actionPolicy.canStart || actionPolicy.canEscalate || actionPolicy.canResolve) && <>
              {actionPolicy.canAssign && user && (isAdmin
                ? <AdminComplaintAssignment
                    key={`${complaint.id}:${complaint.currentHandlerLevel}:${assignmentResetCount}`}
                    complaint={complaint}
                    isPending={assign.isPending}
                    onAssign={(assigneeId) => assign.mutate({ id: complaint.id, assigneeId })}
                  />
                : <Button variant="secondary" disabled={assign.isPending} onClick={() => assign.mutate({ id: complaint.id, assigneeId: user.id })}>Assign to me</Button>)}
              {actionPolicy.canStart && <Button variant="secondary" disabled={start.isPending} onClick={() => start.mutate(complaint.id)}>Start work</Button>}
              {actionPolicy.canEscalate && <form className="stack-form" onSubmit={submitEscalation}><h3>Escalate</h3><Label htmlFor="escalation-reason">Reason</Label><textarea id="escalation-reason" required maxLength={1000} value={escalationReason} onChange={(event) => setEscalationReason(event.target.value)} /><Button variant="secondary" disabled={escalate.isPending}>Escalate to next level</Button></form>}
              {actionPolicy.canResolve && <form className="stack-form" onSubmit={submitResolution}><h3>Resolve complaint</h3><Label htmlFor="resolution-note">Resolution note</Label><textarea id="resolution-note" required maxLength={2000} value={resolutionNote} onChange={(event) => setResolutionNote(event.target.value)} /><Button disabled={resolve.isPending}>Resolve</Button></form>}
            </>}
            <section><h3>Complaint history</h3>{history.isPending && <LoadingState label="Loading history" />}{history.isError && <ErrorState title="History unavailable" message={history.error.message} onRetry={() => void history.refetch()} />}{history.data?.length === 0 && <EmptyState title="No history yet" message="Events are added as the complaint is handled." />}{history.data && history.data.length > 0 && <ol className="event-list">{history.data.map((event) => <li key={event.id}><strong>{event.action}</strong><span>{formatDate(event.occurredAt)} · {event.fromLevel ?? '—'} → {event.toLevel ?? '—'}</span>{event.note && <span>{event.note}</span>}</li>)}</ol>}</section>
          </>}
        </CardContent></Card> : <EmptyState title="Select a complaint" message="Choose a record to see details, available actions, and its event history." />}
      </div>
    </div>
  )
}

type AdminComplaintAssignmentProps = {
  complaint: Complaint
  isPending: boolean
  onAssign: (assigneeId: string) => void
}

function AdminComplaintAssignment({ complaint, isPending, onAssign }: AdminComplaintAssignmentProps) {
  const [search, setSearch] = useState('')
  const [offset, setOffset] = useState(0)
  const [selectedAssigneeId, setSelectedAssigneeId] = useState('')
  const users = useQuery({
    queryKey: ['users', 'complaint-assignment', search, offset],
    queryFn: ({ signal }) => listManagedUsers(search, offset, pageSize, signal),
  })
  const eligibleAssignees = users.data?.filter((candidate) =>
    candidate.status === 'ACTIVE'
      && candidate.roles.some((role) => role === complaint.currentHandlerLevel)) ?? []
  const hasMoreUsers = (users.data?.length ?? 0) === pageSize

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (selectedAssigneeId && eligibleAssignees.some((candidate) => candidate.id === selectedAssigneeId)) {
      onAssign(selectedAssigneeId)
    }
  }

  return (
    <form className="stack-form complaint-assignment-form" onSubmit={submit}>
      <Label htmlFor="complaint-assignee-search">Search staff by name or email</Label>
      <Input
        id="complaint-assignee-search"
        type="search"
        value={search}
        disabled={isPending}
        onChange={(event) => {
          setSearch(event.target.value)
          setOffset(0)
          setSelectedAssigneeId('')
        }}
        aria-describedby="complaint-assignment-help"
      />
      <p id="complaint-assignment-help" className="muted form-hint">
        Showing active accounts with the {displayHandlerLevel(complaint.currentHandlerLevel)} role. Eligibility is verified again when assigning.
      </p>
      {users.isFetching && <LoadingState label="Loading staff candidates" />}
      {users.isError && <ErrorState
        title="Staff candidates unavailable"
        message={users.error.message}
        onRetry={() => void users.refetch()}
      />}
      {users.data?.length === 0 && <p className="muted" role="status">
        {offset === 0 ? 'No users match this search.' : 'No more users are available for this search.'}
      </p>}
      {users.data && users.data.length > 0 && <>
        <Label htmlFor="complaint-assignee">Eligible staff member</Label>
        <select
          id="complaint-assignee"
          required
          value={selectedAssigneeId}
          disabled={isPending || users.isFetching || users.isError || eligibleAssignees.length === 0}
          onChange={(event) => setSelectedAssigneeId(event.target.value)}
        >
          <option value="">Select an eligible staff member</option>
          {eligibleAssignees.map((candidate) => (
            <option key={candidate.id} value={candidate.id}>
              {candidate.fullName} ({candidate.email})
            </option>
          ))}
        </select>
        {eligibleAssignees.length === 0 && <p className="muted" role="status">
          {hasMoreUsers
            ? 'No eligible staff on this page. More users may be available.'
            : offset === 0
              ? 'No eligible staff match this search.'
              : 'No eligible staff on this page. End of available results for this search.'}
        </p>}
      </>}
      {users.data && <div className="pagination">
        <Button
          type="button"
          variant="secondary"
          disabled={offset === 0 || isPending}
          onClick={() => {
            setOffset(Math.max(0, offset - pageSize))
            setSelectedAssigneeId('')
          }}
        >
          Previous users
        </Button>
        <span>
          {users.data.length > 0 ? `Users ${offset + 1}–${offset + users.data.length}` : 'No users on this page'}
        </span>
        <Button
          type="button"
          variant="secondary"
          disabled={!hasMoreUsers || isPending}
          onClick={() => {
            setOffset(offset + pageSize)
            setSelectedAssigneeId('')
          }}
        >
          More users
        </Button>
      </div>}
      <Button
        variant="secondary"
        disabled={isPending || users.isFetching || users.isError || !selectedAssigneeId || !eligibleAssignees.some((candidate) => candidate.id === selectedAssigneeId)}
      >
        Assign complaint
      </Button>
    </form>
  )
}
