import { apiRequest } from '@/lib/api/client'
import { apiMutation } from '@/lib/api/mutations'

export type Streetlight = {
  id: string
  assetTag: string
  profileId: string
  profileVersion: number
  streetZoneId: string
  latitude: number
  longitude: number
  addressLabel: string | null
  operationalState: string
  brightnessOverridePct?: number | null
  effectiveBrightnessPct: number
  installedOn: string | null
  lastSeenAt: string | null
  registeredAt: string
}

export type LightingProfile = {
  id: string
  profileCode: string
  profileVersion: number
  manufacturer: string
  model: string
  wattage: number
  defaultBrightnessPct: number
  controllerType: string
  firmwareFamily: string
  configurationJson: string
  active: boolean
  createdAt: string
}

export type StreetlightStats = {
  persistedStreetlights: number
  distinctActiveProfiles: number
  cachedFlyweights: number
  cacheCapacity: number
  cacheHits: number
  cacheMisses: number
  cacheEvictions: number
  contextsProcessed: number
  contextToFlyweightRatio: number
}

const root = '/smartcity360/api/v1'
const page = (offset: number, limit: number) => new URLSearchParams({ offset: String(offset), limit: String(limit) })

export function listStreetlights(offset: number, limit: number, signal?: AbortSignal) {
  return apiRequest<Streetlight[]>(`${root}/streetlights?${page(offset, limit)}`, { signal })
}

export function getStreetlight(id: string, signal?: AbortSignal) {
  return apiRequest<Streetlight>(`${root}/streetlights/${encodeURIComponent(id)}`, { signal })
}

export function getStreetlightStats(signal?: AbortSignal) {
  return apiRequest<StreetlightStats>(`${root}/streetlights/stats`, { signal })
}

export function getEffectiveProfile(id: string, signal?: AbortSignal) {
  return apiRequest<LightingProfile>(`${root}/streetlights/${encodeURIComponent(id)}/profile`, { signal })
}

export function listLightingProfiles(signal?: AbortSignal) {
  return apiRequest<LightingProfile[]>(`${root}/lighting-profiles?${page(0, 100)}&activeOnly=true`, { signal })
}

export const registerStreetlight = (body: {
  assetTag: string
  profileId: string
  streetZoneId: string
  latitude: number
  longitude: number
  addressLabel: string
  installedOn: string | null
}) => apiMutation<Streetlight>(`${root}/streetlights`, { method: 'POST', body })

export const createLightingProfile = (body: {
  profileCode: string
  manufacturer: string
  model: string
  wattage: number
  defaultBrightnessPct: number
  controllerType: string
  firmwareFamily: string
  configurationJson: string
}) => apiMutation<LightingProfile>(`${root}/lighting-profiles`, { method: 'POST', body })

export const assignLightingProfile = (id: string, profileId: string) =>
  apiMutation<Streetlight>(`${root}/streetlights/${encodeURIComponent(id)}/profile`, { method: 'POST', body: { profileId } })

export const setStreetlightState = (id: string, state: 'on' | 'off') =>
  apiMutation<{ streetlightId: string; command: string; previousState: string; currentState: string; changed: boolean; brightnessOverridePct: number | null; effectiveBrightnessPct: number }>(
    `${root}/streetlights/${encodeURIComponent(id)}/${state}`,
    { method: 'POST' },
  )

export const setStreetlightBrightness = (id: string, brightnessPct: number) =>
  apiMutation<{ streetlightId: string; command: string; previousState: string; currentState: string; changed: boolean; brightnessOverridePct: number | null; effectiveBrightnessPct: number }>(
    `${root}/streetlights/${encodeURIComponent(id)}/brightness`,
    { method: 'POST', body: { brightnessPct } },
  )
