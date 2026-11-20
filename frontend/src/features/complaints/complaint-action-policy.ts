import type { SmartCityRole } from '@/lib/api/contracts'
import type { Complaint } from './complaint-api'

type ComplaintActor = {
  id: string
  roles: readonly SmartCityRole[]
}

type ComplaintActionPolicy = {
  canAssign: boolean
  canStart: boolean
  canEscalate: boolean
  canResolve: boolean
}

const terminalStatuses = new Set(['RESOLVED', 'REJECTED'])

export function complaintActionPolicy(
  complaint: Complaint | undefined,
  actor: ComplaintActor | null | undefined,
): ComplaintActionPolicy {
  if (!complaint || !actor || terminalStatuses.has(complaint.status)) {
    return noActions()
  }

  const assignedToUserId: unknown = complaint.assignedToUserId
  if (assignedToUserId !== null && typeof assignedToUserId !== 'string') {
    return noActions()
  }

  const isAdmin = actor.roles.includes('ADMIN')
  const isCurrentHandler = isHandlerRole(complaint.currentHandlerLevel)
    && actor.roles.includes(complaint.currentHandlerLevel)
  const isAssignedToActor = assignedToUserId === actor.id
  const isAssigned = assignedToUserId !== null
  const canAccessCurrentHandler = isAdmin || isCurrentHandler
  const canActOnAssignedComplaint = isAssigned
    && (isAdmin || (isCurrentHandler && isAssignedToActor))

  return {
    canAssign: !isAssigned && canAccessCurrentHandler,
    canStart: canActOnAssignedComplaint && complaint.status !== 'IN_PROGRESS',
    canEscalate: canActOnAssignedComplaint && complaint.currentHandlerLevel !== 'CITY_MANAGER',
    canResolve: canActOnAssignedComplaint && complaint.status === 'IN_PROGRESS',
  }
}

function isHandlerRole(role: string): role is 'LOCAL_STAFF' | 'SUPERVISOR' | 'CITY_MANAGER' {
  return role === 'LOCAL_STAFF' || role === 'SUPERVISOR' || role === 'CITY_MANAGER'
}

function noActions(): ComplaintActionPolicy {
  return {
    canAssign: false,
    canStart: false,
    canEscalate: false,
    canResolve: false,
  }
}
