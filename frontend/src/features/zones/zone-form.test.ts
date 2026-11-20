import { describe, expect, it } from 'vitest'
import {
  createZonePayload,
  filterEligibleParents,
  formatZoneCode,
  getParentType,
  isCreateZoneValid,
  resetParentForType,
  type CreateZoneForm,
} from './zone-form'

const form = (overrides: Partial<CreateZoneForm> = {}): CreateZoneForm => ({
  type: 'DISTRICT',
  code: 'D-1',
  name: 'District 1',
  parentId: 'city-1',
  ...overrides,
})

describe('zone creation form rules', () => {
  it.each([
    ['CITY', '001', 'C001'],
    ['DISTRICT', '001', 'D001'],
    ['STREET', '001', 'S001'],
    ['BUILDING', '001', 'B001'],
  ] as const)('formats numeric %s input as %s', (type, input, expected) => {
    expect(formatZoneCode(type, input)).toEqual({ code: expected })
  })

  it('keeps a correctly prefixed code unchanged', () => {
    expect(formatZoneCode('DISTRICT', 'D001')).toEqual({ code: 'D001' })
  })

  it('rejects an incompatible prefix, invalid characters, and blank input', () => {
    expect(formatZoneCode('CITY', 'D001').code).toBeNull()
    expect(formatZoneCode('CITY', 'C-001').code).toBeNull()
    expect(formatZoneCode('CITY', '').code).toBeNull()
  })

  it('creates a CITY with an explicit null parent', () => {
    expect(isCreateZoneValid(form({ type: 'CITY', code: '001', parentId: '' }))).toBe(true)
    expect(createZonePayload(form({ type: 'CITY', code: '001', parentId: '' }))).toEqual({
      type: 'CITY', code: 'C001', name: 'District 1', parentId: null,
    })
  })

  it('submits the generated canonical code', () => {
    expect(createZonePayload(form({ type: 'DISTRICT', code: '001' }))).toEqual({
      type: 'DISTRICT', code: 'D001', name: 'District 1', parentId: 'city-1',
    })
  })

  it('requires a parent for every non-CITY zone type', () => {
    expect(isCreateZoneValid(form({ type: 'DISTRICT', parentId: '' }))).toBe(false)
    expect(isCreateZoneValid(form({ type: 'STREET', parentId: '' }))).toBe(false)
    expect(isCreateZoneValid(form({ type: 'BUILDING', parentId: '' }))).toBe(false)
  })

  it.each([
    ['DISTRICT', 'CITY'],
    ['STREET', 'DISTRICT'],
    ['BUILDING', 'STREET'],
  ] as const)('maps %s to eligible parent type %s', (type, expected) => {
    expect(getParentType(type)).toBe(expected)
  })

  it('resets an incompatible parent when the type changes', () => {
    expect(resetParentForType(form({ type: 'DISTRICT', parentId: 'city-1' }), 'STREET')).toEqual({
      type: 'STREET', code: 'D-1', name: 'District 1', parentId: '',
    })
  })

  it('does not allow a CITY to submit a parent', () => {
    expect(isCreateZoneValid(form({ type: 'CITY', parentId: 'city-1' }))).toBe(false)
  })

  it('filters inactive and wrong-type parent records', () => {
    expect(filterEligibleParents([
      { id: 'city-1', parentId: null, type: 'CITY', code: 'C-1', name: 'City 1', status: 'ACTIVE', createdAt: '', updatedAt: '' },
      { id: 'city-2', parentId: null, type: 'CITY', code: 'C-2', name: 'City 2', status: 'INACTIVE', createdAt: '', updatedAt: '' },
      { id: 'district-1', parentId: 'city-1', type: 'DISTRICT', code: 'D-1', name: 'District 1', status: 'ACTIVE', createdAt: '', updatedAt: '' },
    ], 'DISTRICT').map((zone) => zone.id)).toEqual(['city-1'])
  })
})
