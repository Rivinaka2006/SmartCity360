import { describe, expect, it } from 'vitest'
import { complaintActionPolicy } from './complaint-action-policy'
import type { Complaint } from './complaint-api'

const complaint = (overrides: Partial<Complaint> = {}): Complaint => ({
  id: 'complaint-1',
  referenceNo: 'SC-001',
  residentId: 'resident-1',
  zoneId: 'zone-1',
  title: 'Streetlight outage',
  description: 'The streetlight is not operating.',
  severity: 'MEDIUM',
  status: 'OPEN',
  assignedToUserId: 'staff-1',
  currentHandlerLevel: 'LOCAL_STAFF',
  createdAt: '2026-11-07T00:00:00Z',
  updatedAt: '2026-11-07T00:00:00Z',
  resolvedAt: null,
  ...overrides,
})

describe('complaint action policy', () => {
  it('allows an ADMIN to act on a complaint assigned to another eligible user', () => {
    const policy = complaintActionPolicy(complaint(), {
      id: 'admin-1',
      roles: ['ADMIN'],
    })

    expect(policy.canAssign).toBe(false)
    expect(policy.canStart).toBe(true)
    expect(policy.canEscalate).toBe(true)
    expect(policy.canResolve).toBe(false)
  })

  it('allows an ADMIN to assign an unassigned complaint but not to start it', () => {
    const policy = complaintActionPolicy(complaint({ assignedToUserId: null }), {
      id: 'admin-1',
      roles: ['ADMIN'],
    })

    expect(policy.canAssign).toBe(true)
    expect(policy.canStart).toBe(false)
    expect(policy.canEscalate).toBe(false)
    expect(policy.canResolve).toBe(false)
  })

  it('allows LOCAL_STAFF to act only on a complaint assigned to that staff member', () => {
    const assignedPolicy = complaintActionPolicy(complaint(), {
      id: 'staff-1',
      roles: ['LOCAL_STAFF'],
    })
    const unassignedPolicy = complaintActionPolicy(complaint(), {
      id: 'staff-2',
      roles: ['LOCAL_STAFF'],
    })

    expect(assignedPolicy.canStart).toBe(true)
    expect(assignedPolicy.canEscalate).toBe(true)
    expect(unassignedPolicy.canStart).toBe(false)
    expect(unassignedPolicy.canEscalate).toBe(false)
  })

  it('allows staff at the current handler level to assign an unassigned complaint to themselves', () => {
    const policy = complaintActionPolicy(complaint({ assignedToUserId: null }), {
      id: 'staff-1',
      roles: ['LOCAL_STAFF'],
    })

    expect(policy.canAssign).toBe(true)
    expect(policy.canStart).toBe(false)
  })

  it('hides assignment from a SUPERVISOR when the current handler is LOCAL_STAFF', () => {
    const policy = complaintActionPolicy(complaint({ assignedToUserId: null }), {
      id: 'supervisor-1',
      roles: ['SUPERVISOR'],
    })

    expect(policy.canAssign).toBe(false)
  })

  it('does not treat a malformed missing assignment property as unassigned', () => {
    const malformedComplaint = {
      ...complaint({ assignedToUserId: null }),
      assignedToUserId: undefined,
    } as unknown as Complaint
    const policy = complaintActionPolicy(malformedComplaint, {
      id: 'staff-1',
      roles: ['LOCAL_STAFF'],
    })

    expect(policy.canAssign).toBe(false)
  })

  it('requires a staff role that matches the current handler level', () => {
    const supervisorAtLocalStaff = complaintActionPolicy(complaint(), {
      id: 'staff-1',
      roles: ['SUPERVISOR'],
    })
    const cityManagerAtCityManager = complaintActionPolicy(complaint({
      currentHandlerLevel: 'CITY_MANAGER',
    }), {
      id: 'staff-1',
      roles: ['CITY_MANAGER'],
    })
    const supervisorAtSupervisor = complaintActionPolicy(complaint({
      currentHandlerLevel: 'SUPERVISOR',
    }), {
      id: 'staff-1',
      roles: ['SUPERVISOR'],
    })

    expect(supervisorAtLocalStaff.canStart).toBe(false)
    expect(supervisorAtSupervisor.canStart).toBe(true)
    expect(cityManagerAtCityManager.canStart).toBe(true)
    expect(cityManagerAtCityManager.canEscalate).toBe(false)
  })

  it('never exposes workflow actions to a RESIDENT', () => {
    const policy = complaintActionPolicy(complaint(), {
      id: 'resident-1',
      roles: ['RESIDENT'],
    })

    expect(policy.canAssign).toBe(false)
    expect(policy.canStart).toBe(false)
    expect(policy.canEscalate).toBe(false)
    expect(policy.canResolve).toBe(false)
  })

  it('hides all workflow actions for terminal complaints and only resolves IN_PROGRESS complaints', () => {
    const inProgress = complaintActionPolicy(complaint({ status: 'IN_PROGRESS' }), {
      id: 'staff-1',
      roles: ['LOCAL_STAFF'],
    })
    const resolved = complaintActionPolicy(complaint({ status: 'RESOLVED' }), {
      id: 'admin-1',
      roles: ['ADMIN'],
    })

    expect(inProgress.canStart).toBe(false)
    expect(inProgress.canResolve).toBe(true)
    expect(resolved.canAssign).toBe(false)
    expect(resolved.canStart).toBe(false)
    expect(resolved.canEscalate).toBe(false)
    expect(resolved.canResolve).toBe(false)
  })
})
