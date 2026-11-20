import type { Zone } from './zone-api'

export type ZoneType = 'CITY' | 'DISTRICT' | 'STREET' | 'BUILDING'

export type CreateZoneForm = {
  type: ZoneType
  code: string
  name: string
  parentId: string
}

const numericCode = /^\d{1,39}$/
const prefixedCode = /^[CDSB]\d{1,39}$/
const prefixes: Record<ZoneType, string> = {
  CITY: 'C',
  DISTRICT: 'D',
  STREET: 'S',
  BUILDING: 'B',
}

export type ZoneCodeFormat = {
  code: string | null
  error?: string
}

export function formatZoneCode(type: ZoneType, value: string): ZoneCodeFormat {
  if (numericCode.test(value)) return { code: `${prefixes[type]}${value}` }
  if (!prefixedCode.test(value)) {
    return { code: null, error: 'Enter digits only, or the matching type prefix followed by digits.' }
  }
  if (!value.startsWith(prefixes[type])) {
    return { code: null, error: `${type} codes must start with ${prefixes[type]}.` }
  }
  return { code: value }
}

export function getParentType(type: ZoneType): Exclude<ZoneType, 'BUILDING'> | null {
  if (type === 'CITY') return null
  if (type === 'DISTRICT') return 'CITY'
  if (type === 'STREET') return 'DISTRICT'
  return 'STREET'
}

export function resetParentForType(form: CreateZoneForm, type: ZoneType): CreateZoneForm {
  return { ...form, type, parentId: '' }
}

export function isCreateZoneValid(form: CreateZoneForm): boolean {
  const parentType = getParentType(form.type)
  const hasValidParent = parentType === null ? form.parentId === '' : form.parentId.trim().length > 0
  return formatZoneCode(form.type, form.code).code !== null
    && form.name.trim().length > 0 && form.name.trim().length <= 150
    && hasValidParent
}

export function createZonePayload(form: CreateZoneForm) {
  const formattedCode = formatZoneCode(form.type, form.code)
  if (formattedCode.code === null) throw new Error(formattedCode.error)
  return {
    type: form.type,
    code: formattedCode.code,
    name: form.name,
    parentId: form.parentId || null,
  }
}

export function filterEligibleParents(zones: Zone[], type: ZoneType): Zone[] {
  const parentType = getParentType(type)
  if (parentType === null) return []
  return zones.filter((zone) => zone.type === parentType && zone.status === 'ACTIVE')
}
