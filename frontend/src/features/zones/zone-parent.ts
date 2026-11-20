import type { Zone } from './zone-api'

export function parentLabel(parent: Zone | undefined): string {
  if (!parent) return 'Parent zone unavailable'
  return `${parent.name} (${parent.type} · ${parent.code})`
}
