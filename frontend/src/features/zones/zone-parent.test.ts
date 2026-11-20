import { describe, expect, it } from 'vitest'
import { parentLabel } from './zone-parent'

describe('zone parent labels', () => {
  it('shows parent name, type, and code', () => {
    expect(parentLabel({ id: 'street', parentId: null, type: 'STREET', code: 'S001', name: 'Gannoruwa', status: 'ACTIVE', createdAt: '', updatedAt: '' }))
      .toBe('Gannoruwa (STREET · S001)')
  })

  it('shows an unavailable parent safely', () => {
    expect(parentLabel(undefined)).toBe('Parent zone unavailable')
  })
})
