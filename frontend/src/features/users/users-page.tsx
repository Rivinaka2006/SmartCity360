import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { useAuth } from '@/features/auth/use-auth'
import type { SmartCityRole } from '@/lib/api/contracts'
import { createManagedUser, listManagedUsers, updateManagedUserRoles, updateManagedUserStatus, USERS_PER_PAGE, type ManagedUser } from './user-api'

const requestLimit = USERS_PER_PAGE + 1
const roles: SmartCityRole[] = ['RESIDENT', 'LOCAL_STAFF', 'SUPERVISOR', 'CITY_MANAGER', 'ADMIN']
const statuses: ManagedUser['status'][] = ['ACTIVE', 'SUSPENDED', 'DISABLED']

export function UsersPage() {
  const auth = useAuth()
  const currentUserId = auth.status === 'authenticated' ? auth.user?.id : undefined
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [appliedSearch, setAppliedSearch] = useState('')
  const [offset, setOffset] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'RESIDENT' as SmartCityRole, integrationTestAccount: true })
  const users = useQuery({ queryKey: ['users', appliedSearch, offset], queryFn: ({ signal }) => listManagedUsers(appliedSearch, offset, requestLimit, signal) })
  const refresh = async (message: string) => {
    setError('')
    setNotice(message)
    await queryClient.invalidateQueries({ queryKey: ['users'] })
  }
  const create = useMutation({
    mutationFn: createManagedUser,
    onSuccess: async () => {
      setForm({ fullName: '', email: '', password: '', role: 'RESIDENT', integrationTestAccount: true })
      await refresh('User account created.')
    },
    onError: (reason) => setError(reason.message),
  })
  const changeStatus = useMutation({
    mutationFn: ({ id, status }: { id: string; status: ManagedUser['status'] }) => updateManagedUserStatus(id, status),
    onSuccess: async () => refresh('User status updated.'),
    onError: (reason) => setError(reason.message),
  })
  const changeRoles = useMutation({
    mutationFn: ({ id, role }: { id: string; role: SmartCityRole }) => updateManagedUserRoles(id, [role]),
    onSuccess: async () => refresh('User role updated.'),
    onError: (reason) => setError(reason.message),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    if (form.password.length < 14) {
      setError('The initial password must contain at least 14 characters.')
      return
    }
    create.mutate(form)
  }

  function applySearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setOffset(0)
    setAppliedSearch(search)
  }

  function confirmStatus(user: ManagedUser, status: ManagedUser['status']) {
    if (!currentUserId || user.id === currentUserId) return
    if (window.confirm(`Change ${user.fullName}'s status to ${status}?`)) {
      changeStatus.mutate({ id: user.id, status })
    }
  }

  function confirmRole(user: ManagedUser, role: SmartCityRole) {
    if (!currentUserId || user.id === currentUserId) return
    if (window.confirm(`Replace ${user.fullName}'s current roles with ${role}?`)) {
      changeRoles.mutate({ id: user.id, role })
    }
  }

  const visibleUsers = users.data?.slice(0, USERS_PER_PAGE)
  const hasNextPage = (users.data?.length ?? 0) > USERS_PER_PAGE

  return (
    <div className="feature-page feature-users">
      <header className="page-heading"><p className="eyebrow">Administration</p><h1>User and role management</h1><p className="muted">Create controlled accounts and manage role or account status. Passwords are never displayed or retained in this browser.</p></header>
      {error && <p className="form-error" role="alert">{error}</p>}
      {notice && <p className="notice-state" role="status">{notice}</p>}
      <div className="feature-columns">
          <Card className="users-card"><CardHeader><CardTitle>Users</CardTitle></CardHeader><CardContent>
            <form className="inline-actions user-search-form" onSubmit={applySearch}><Label htmlFor="user-search">Search users</Label><Input id="user-search" value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Name or email" /><Button variant="secondary">Search</Button></form>
            {users.isPending && <LoadingState label="Loading users" />}
            {users.isError && <ErrorState title="Users unavailable" message={users.error.message} onRetry={() => void users.refetch()} />}
            {users.data?.length === 0 && <EmptyState title="No users found" message="No accounts match the current search." />}
            {visibleUsers && visibleUsers.length > 0 && <ul className="record-list user-record-list">{visibleUsers.map((user) => {
              const isCurrentUser = Boolean(currentUserId) && user.id === currentUserId
              const controlsDisabled = !currentUserId || isCurrentUser
              const explanationId = `user-management-controls-${user.id}`
              return <li key={user.id}><strong>{user.fullName}{user.integrationTestAccount ? ' · Integration test' : ''}</strong><span className="user-record-meta">{user.email} <span className="status-pill" data-state={user.status.toLowerCase()}>{user.status}</span></span><span className="user-role-list">{user.roles.map((role) => <span className="role-badge" key={role}>{role.replaceAll('_', ' ')}</span>)}</span>{(isCurrentUser || !currentUserId) && <p id={explanationId} className="muted" role="note">{isCurrentUser ? 'You cannot change your own administrator role or account status.' : 'User role and status controls are unavailable until your account identity is verified.'}</p>}<div className="inline-actions"><label>Role<select aria-label={`Role for ${user.fullName}`} aria-describedby={(isCurrentUser || !currentUserId) ? explanationId : undefined} disabled={controlsDisabled} value={user.roles[0] ?? ''} onChange={(event) => confirmRole(user, event.target.value as SmartCityRole)}>{roles.map((role) => <option key={role}>{role}</option>)}</select></label><label>Status<select aria-label={`Status for ${user.fullName}`} aria-describedby={(isCurrentUser || !currentUserId) ? explanationId : undefined} disabled={controlsDisabled} value={user.status} onChange={(event) => confirmStatus(user, event.target.value as ManagedUser['status'])}>{statuses.map((status) => <option key={status}>{status}</option>)}</select></label></div></li>
            })}</ul>}
            {visibleUsers && visibleUsers.length > 0 && <div className="pagination"><Button variant="secondary" disabled={!offset} onClick={() => setOffset(Math.max(0, offset - USERS_PER_PAGE))}>Previous</Button><span>Records {offset + 1}–{offset + visibleUsers.length}</span><Button variant="secondary" disabled={!hasNextPage} onClick={() => setOffset(offset + USERS_PER_PAGE)}>Next</Button></div>}
          </CardContent></Card>
        <Card className="user-create-card"><CardHeader><CardTitle>Create controlled user</CardTitle></CardHeader><CardContent><form className="stack-form" onSubmit={submit}>
          <Label htmlFor="user-full-name">Full name</Label><Input id="user-full-name" required maxLength={120} value={form.fullName} onChange={(event) => setForm({ ...form, fullName: event.target.value })} />
          <Label htmlFor="user-email">Email</Label><Input id="user-email" type="email" required maxLength={255} autoComplete="off" value={form.email} onChange={(event) => setForm({ ...form, email: event.target.value })} />
          <Label htmlFor="user-password">Initial password</Label><Input id="user-password" type="password" required minLength={14} autoComplete="new-password" value={form.password} onChange={(event) => setForm({ ...form, password: event.target.value })} /><p className="muted form-hint">At least 14 characters. The password is sent only for this HTTPS request and is not stored by the application UI.</p>
          <label>Initial role<select value={form.role} onChange={(event) => setForm({ ...form, role: event.target.value as SmartCityRole })}>{roles.map((role) => <option key={role}>{role}</option>)}</select></label>
          <label><input type="checkbox" checked={form.integrationTestAccount} onChange={(event) => setForm({ ...form, integrationTestAccount: event.target.checked })} /> Label as integration-test account</label>
          <Button disabled={create.isPending}>Create user</Button>
        </form></CardContent></Card>
      </div>
    </div>
  )
}
