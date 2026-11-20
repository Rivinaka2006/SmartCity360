import { describe, expect, it } from 'vitest'
import type { SmartCityRole } from '@/lib/api/contracts'
import { availableNavigation, sanitizeReturnPath } from './route-policy'

describe('route policy', () => {
  it.each([
    ['ADMIN', ['dashboard', 'zones', 'complaints', 'streetlights', 'energy', 'policies', 'users']],
    ['CITY_MANAGER', ['dashboard', 'zones', 'complaints', 'streetlights', 'energy', 'policies']],
    ['SUPERVISOR', ['dashboard', 'zones', 'complaints', 'streetlights', 'energy', 'policies']],
    ['LOCAL_STAFF', ['dashboard', 'zones', 'complaints', 'streetlights', 'policies']],
    ['RESIDENT', ['dashboard', 'complaints']],
  ] as const)('returns backend-compatible navigation for %s', (role, expectedIds) => {
    expect(availableNavigation([role as SmartCityRole]).map((route) => route.id)).toEqual(expectedIds)
  })

  it('uses the union of permissions for callers with multiple roles without duplicate links', () => {
    expect(availableNavigation(['RESIDENT', 'SUPERVISOR']).map((route) => route.id)).toEqual([
      'dashboard', 'zones', 'complaints', 'streetlights', 'energy', 'policies',
    ])
  })

  it('reserves user management navigation for ADMIN', () => {
    expect(availableNavigation(['ADMIN']).some((route) => route.id === 'users')).toBe(true)
    expect(availableNavigation(['CITY_MANAGER']).some((route) => route.id === 'users')).toBe(false)
    expect(availableNavigation(['RESIDENT']).some((route) => route.id === 'users')).toBe(false)
  })

  it.each([
    ['https://evil.example/app', '/app'],
    ['//evil.example/app', '/app'],
    ['/\\evil.example/app', '/app'],
    ['/login', '/app'],
    ['', '/app'],
    ['/app/complaints?status=open', '/app/complaints?status=open'],
  ])('sanitizes return path %s as %s', (candidate, expected) => {
    expect(sanitizeReturnPath(candidate)).toBe(expected)
  })
})
