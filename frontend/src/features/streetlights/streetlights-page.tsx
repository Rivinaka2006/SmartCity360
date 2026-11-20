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
import { listZoneParents } from '@/features/zones/zone-api'
import { assignLightingProfile, createLightingProfile, getEffectiveProfile, getStreetlight, getStreetlightStats, listLightingProfiles, listStreetlights, registerStreetlight, setStreetlightBrightness, setStreetlightState, type Streetlight } from './streetlight-api'

const pageSize = 25

export function StreetlightsPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const [offset, setOffset] = useState(0)
  const [selectedId, setSelectedId] = useState('')
  const [formError, setFormError] = useState('')
  const [notice, setNotice] = useState('')
  const [brightness, setBrightness] = useState(75)
  const [profileId, setProfileId] = useState('')
  const [registration, setRegistration] = useState({ assetTag: '', profileId: '', streetZoneId: '', latitude: '', longitude: '', addressLabel: '', installedOn: '' })
  const [streetZoneSearch, setStreetZoneSearch] = useState('')
  const [streetZoneOffset, setStreetZoneOffset] = useState(0)
  const [profileForm, setProfileForm] = useState({ profileCode: '', manufacturer: '', model: '', wattage: '', defaultBrightnessPct: '75', controllerType: '', firmwareFamily: '', configurationJson: '{}' })
  const isAdmin = user?.roles.includes('ADMIN') ?? false
  const canOperate = user?.roles.some((role) => role === 'ADMIN' || role === 'CITY_MANAGER') ?? false
  const lights = useQuery({ queryKey: ['streetlights', offset], queryFn: ({ signal }) => listStreetlights(offset, pageSize, signal) })
  const profiles = useQuery({ queryKey: ['lighting-profiles'], queryFn: ({ signal }) => listLightingProfiles(signal) })
  const stats = useQuery({ queryKey: ['streetlight-stats'], queryFn: ({ signal }) => getStreetlightStats(signal) })
  const streetZones = useQuery({
    queryKey: ['zones', 'streetlight-registration', 'STREET', streetZoneSearch, streetZoneOffset],
    queryFn: ({ signal }) => listZoneParents('STREET', streetZoneSearch, streetZoneOffset, pageSize, signal),
    enabled: isAdmin,
  })
  const selectedSummary = lights.data?.find((item) => item.id === selectedId)
  const detail = useQuery({ queryKey: ['streetlights', 'detail', selectedId], queryFn: ({ signal }) => getStreetlight(selectedId, signal), enabled: Boolean(selectedId) })
  const profile = useQuery({ queryKey: ['streetlights', selectedId, 'profile'], queryFn: ({ signal }) => getEffectiveProfile(selectedId, signal), enabled: Boolean(selectedId) })
  const refresh = async (light?: Streetlight) => {
    if (light) {
      setSelectedId(light.id)
      setProfileId(light.profileId)
      setBrightness(light.effectiveBrightnessPct)
    }
    setNotice('Streetlight data updated.')
    setFormError('')
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['streetlights'] }),
      queryClient.invalidateQueries({ queryKey: ['lighting-profiles'] }),
      queryClient.invalidateQueries({ queryKey: ['streetlight-stats'] }),
    ])
  }
  const stateMutation = useMutation({ mutationFn: ({ id, state }: { id: string; state: 'on' | 'off' }) => setStreetlightState(id, state), onSuccess: async (result) => { setNotice(`${result.command}: ${result.currentState}, brightness ${result.effectiveBrightnessPct}%.`); setFormError(''); await refresh() }, onError: (error) => setFormError(error.message) })
  const brightnessMutation = useMutation({ mutationFn: ({ id, pct }: { id: string; pct: number }) => setStreetlightBrightness(id, pct), onSuccess: async (result) => { setNotice(`Brightness set to ${result.effectiveBrightnessPct}%.`); setFormError(''); await refresh() }, onError: (error) => setFormError(error.message) })
  const assign = useMutation({ mutationFn: ({ id, profileId: target }: { id: string; profileId: string }) => assignLightingProfile(id, target), onSuccess: refresh, onError: (error) => setFormError(error.message) })
  const register = useMutation({ mutationFn: registerStreetlight, onSuccess: async (light) => { setRegistration({ assetTag: '', profileId: '', streetZoneId: '', latitude: '', longitude: '', addressLabel: '', installedOn: '' }); setStreetZoneSearch(''); setStreetZoneOffset(0); await refresh(light) }, onError: (error) => setFormError(error.message) })
  const addProfile = useMutation({ mutationFn: createLightingProfile, onSuccess: async () => { setNotice('Lighting profile created.'); setFormError(''); setProfileForm({ profileCode: '', manufacturer: '', model: '', wattage: '', defaultBrightnessPct: '75', controllerType: '', firmwareFamily: '', configurationJson: '{}' }); await refresh() }, onError: (error) => setFormError(error.message) })
  const light = detail.data ?? selectedSummary
  const brightnessOverrideLabel = light?.brightnessOverridePct === null || light?.brightnessOverridePct === undefined
    ? 'No override'
    : `${light.brightnessOverridePct}% override`
  const hasSelectedStreetZone = streetZones.data?.some((zone) => zone.id === registration.streetZoneId) ?? false

  function submitRegistration(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError('')
    if (!hasSelectedStreetZone) {
      setFormError('Select an active STREET zone before registering a streetlight.')
      return
    }
    register.mutate({
      ...registration,
      latitude: Number(registration.latitude),
      longitude: Number(registration.longitude),
      installedOn: registration.installedOn || null,
    })
  }

  function submitProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError('')
    try {
      JSON.parse(profileForm.configurationJson)
    } catch {
      setFormError('Profile configuration must be valid JSON.')
      return
    }
    addProfile.mutate({ ...profileForm, wattage: Number(profileForm.wattage), defaultBrightnessPct: Number(profileForm.defaultBrightnessPct) })
  }

  function submitAssignment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedId || !profileId) return
    assign.mutate({ id: selectedId, profileId })
  }

  return (
    <div className="feature-page feature-streetlights">
      <header className="page-heading"><p className="eyebrow">Infrastructure</p><h1>Smart streetlights</h1><p className="muted">Inspect registered assets, profiles, operations, and server-side flyweight statistics.</p></header>
      {formError && <p className="form-error" role="alert">{formError}</p>}{notice && <p className="notice-state" role="status">{notice}</p>}
      <Card><CardHeader><CardTitle>Flyweight statistics</CardTitle></CardHeader><CardContent>
        {stats.isPending && <LoadingState label="Loading flyweight statistics" />}{stats.isError && <ErrorState title="Statistics unavailable" message={stats.error.message} onRetry={() => void stats.refetch()} />}
        {stats.data && <dl className="metrics-grid"><div><dt>Persisted streetlights</dt><dd>{stats.data.persistedStreetlights}</dd></div><div><dt>Active profiles</dt><dd>{stats.data.distinctActiveProfiles}</dd></div><div><dt>Cached flyweights</dt><dd>{stats.data.cachedFlyweights} / {stats.data.cacheCapacity}</dd></div><div><dt>Cache hits / misses</dt><dd>{stats.data.cacheHits} / {stats.data.cacheMisses}</dd></div><div><dt>Evictions</dt><dd>{stats.data.cacheEvictions}</dd></div><div><dt>Contexts processed</dt><dd>{stats.data.contextsProcessed}</dd></div><div><dt>Context-to-flyweight ratio</dt><dd>{stats.data.contextToFlyweightRatio}</dd></div></dl>}
      </CardContent></Card>
      <div className="feature-columns">
        <Card><CardHeader><CardTitle>Streetlights</CardTitle></CardHeader><CardContent>
          {lights.isPending && <LoadingState label="Loading streetlights" />}{lights.isError && <ErrorState title="Streetlights unavailable" message={lights.error.message} onRetry={() => void lights.refetch()} />}
          {lights.data?.length === 0 && <EmptyState title="No streetlights on this page" message="No registered assets are available in this page of results." />}
          {lights.data && lights.data.length > 0 && <>
            <ul className="record-list">{lights.data.map((item) => <li key={item.id}><button className={`record-select${selectedId === item.id ? ' record-select-active' : ''}`} onClick={() => { setSelectedId(item.id); setProfileId(item.profileId); setBrightness(item.effectiveBrightnessPct); setFormError(''); setNotice('') }}>
              <strong>{item.assetTag}</strong><span className="record-meta"><span className="status-pill" data-state={item.operationalState.toLowerCase()}>{item.operationalState}</span><span>{item.effectiveBrightnessPct}% brightness · profile v{item.profileVersion}</span></span><span>{item.addressLabel || item.streetZoneId}</span>
            </button></li>)}</ul>
            <div className="pagination"><Button variant="secondary" disabled={!offset} onClick={() => setOffset(Math.max(0, offset - pageSize))}>Previous</Button><span>Records {offset + 1}–{offset + lights.data.length}</span><Button variant="secondary" disabled={lights.data.length < pageSize} onClick={() => setOffset(offset + pageSize)}>Next</Button></div>
          </>}
        </CardContent></Card>
        <div className="feature-stack">
          {light ? <Card><CardHeader><CardTitle>{light.assetTag}</CardTitle></CardHeader><CardContent className="feature-stack">
            {detail.isError && <ErrorState title="Streetlight details unavailable" message={detail.error.message} onRetry={() => void detail.refetch()} />}
            <dl className="detail-grid"><dt>State</dt><dd><span className="status-pill" data-state={light.operationalState.toLowerCase()}>{light.operationalState}</span></dd><dt>Street zone</dt><dd>{light.streetZoneId}</dd><dt>Coordinates</dt><dd>{light.latitude}, {light.longitude}</dd><dt>Brightness</dt><dd>{light.effectiveBrightnessPct}% effective · {brightnessOverrideLabel}</dd><dt>Installed</dt><dd>{light.installedOn ?? '—'}</dd><dt>Last seen</dt><dd>{light.lastSeenAt ? new Date(light.lastSeenAt).toLocaleString() : '—'}</dd><dt>Registered</dt><dd>{new Date(light.registeredAt).toLocaleString()}</dd></dl>
            <section><h3>Effective lighting profile</h3>{profile.isPending && <LoadingState label="Loading effective profile" />}{profile.isError && <ErrorState title="Profile unavailable" message={profile.error.message} onRetry={() => void profile.refetch()} />}{profile.data && <dl className="detail-grid"><dt>Profile</dt><dd>{profile.data.profileCode} v{profile.data.profileVersion}</dd><dt>Manufacturer</dt><dd>{profile.data.manufacturer}</dd><dt>Model</dt><dd>{profile.data.model}</dd><dt>Wattage</dt><dd>{profile.data.wattage} W</dd><dt>Default brightness</dt><dd>{profile.data.defaultBrightnessPct}%</dd><dt>Controller</dt><dd>{profile.data.controllerType}</dd></dl>}</section>
            {isAdmin && <form className="stack-form" onSubmit={submitAssignment}><h3>Assign profile</h3><label>Active profile<select required value={profileId} onChange={(event) => setProfileId(event.target.value)}><option value="">Select a profile</option>{profiles.data?.map((item) => <option value={item.id} key={item.id}>{item.profileCode} v{item.profileVersion}</option>)}</select></label><Button variant="secondary" disabled={assign.isPending || !profiles.data?.length}>Assign profile</Button></form>}
            {canOperate && <section className="feature-stack"><h3>Controls</h3><div className="inline-actions"><Button variant="secondary" disabled={stateMutation.isPending || light.operationalState === 'ON'} onClick={() => stateMutation.mutate({ id: light.id, state: 'on' })}>Turn on</Button><Button variant="secondary" disabled={stateMutation.isPending || light.operationalState === 'OFF'} onClick={() => stateMutation.mutate({ id: light.id, state: 'off' })}>Turn off</Button></div><form className="stack-form" onSubmit={(event) => { event.preventDefault(); brightnessMutation.mutate({ id: light.id, pct: brightness }) }}><Label htmlFor="light-brightness">Brightness: {brightness}%</Label><Input id="light-brightness" type="range" min="0" max="100" value={brightness} onChange={(event) => setBrightness(Number(event.target.value))} /><Button variant="secondary" disabled={brightnessMutation.isPending}>Apply brightness</Button></form></section>}
          </CardContent></Card> : <EmptyState title="Select a streetlight" message="Choose a registered light to inspect its profile and permitted controls." />}
          {isAdmin && <Card><CardHeader><CardTitle>Register streetlight</CardTitle></CardHeader><CardContent><form className="stack-form" onSubmit={submitRegistration}>
            <Label htmlFor="light-tag">Asset tag</Label><Input id="light-tag" required maxLength={50} value={registration.assetTag} onChange={(event) => setRegistration({ ...registration, assetTag: event.target.value })} />
            <label>Active lighting profile<select required value={registration.profileId} onChange={(event) => setRegistration({ ...registration, profileId: event.target.value })}><option value="">Select a profile</option>{profiles.data?.map((item) => <option key={item.id} value={item.id}>{item.profileCode} v{item.profileVersion}</option>)}</select></label>
            <Label htmlFor="street-zone-search">Find an active STREET zone</Label><Input id="street-zone-search" value={streetZoneSearch} placeholder="Search by zone name or code" onChange={(event) => { setStreetZoneSearch(event.target.value); setStreetZoneOffset(0); setRegistration({ ...registration, streetZoneId: '' }) }} />
            <Label htmlFor="street-zone">Street zone <span aria-hidden="true">*</span></Label><select id="street-zone" required value={registration.streetZoneId} onChange={(event) => setRegistration({ ...registration, streetZoneId: event.target.value })} disabled={streetZones.isPending || streetZones.isError}><option value="">Select an active STREET zone</option>{streetZones.data?.map((zone) => <option key={zone.id} value={zone.id}>{zone.name} ({zone.code})</option>)}</select>
            {streetZones.isPending && <LoadingState label="Loading active STREET zones" />}
            {streetZones.isError && <ErrorState title="Street zones unavailable" message="Street zone choices could not be loaded." onRetry={() => void streetZones.refetch()} />}
            {streetZones.data?.length === 0 && <EmptyState title="No active STREET zones found" message="Create or activate a STREET zone before registering a streetlight." />}
            {streetZones.data && streetZones.data.length > 0 && <div className="pagination"><Button type="button" variant="secondary" disabled={streetZoneOffset === 0} onClick={() => { setStreetZoneOffset(Math.max(0, streetZoneOffset - pageSize)); setRegistration({ ...registration, streetZoneId: '' }) }}>Previous zones</Button><span>Zone results {streetZoneOffset + 1}-{streetZoneOffset + streetZones.data.length}</span><Button type="button" variant="secondary" disabled={streetZones.data.length < pageSize} onClick={() => { setStreetZoneOffset(streetZoneOffset + pageSize); setRegistration({ ...registration, streetZoneId: '' }) }}>Next zones</Button></div>}
            <div className="form-row"><label>Latitude<Input required type="number" step="any" min="-90" max="90" value={registration.latitude} onChange={(event) => setRegistration({ ...registration, latitude: event.target.value })} /></label><label>Longitude<Input required type="number" step="any" min="-180" max="180" value={registration.longitude} onChange={(event) => setRegistration({ ...registration, longitude: event.target.value })} /></label></div>
            <Label htmlFor="light-address">Address label</Label><Input id="light-address" maxLength={255} value={registration.addressLabel} onChange={(event) => setRegistration({ ...registration, addressLabel: event.target.value })} />
            <Label htmlFor="light-installed">Installed on</Label><Input id="light-installed" type="date" value={registration.installedOn} onChange={(event) => setRegistration({ ...registration, installedOn: event.target.value })} />
            <Button disabled={register.isPending || !profiles.data?.length || !hasSelectedStreetZone || streetZones.isPending || streetZones.isError}>Register</Button>
          </form></CardContent></Card>}
          {isAdmin && <Card><CardHeader><CardTitle>Lighting profiles</CardTitle></CardHeader><CardContent className="feature-stack">
            {profiles.isPending && <LoadingState label="Loading profiles" />}{profiles.isError && <ErrorState title="Profiles unavailable" message={profiles.error.message} onRetry={() => void profiles.refetch()} />}{profiles.data?.length === 0 && <EmptyState title="No active profiles" message="The API returned no active lighting profiles." />}
            {profiles.data && <ul className="event-list">{profiles.data.map((item) => <li key={item.id}><strong>{item.profileCode} v{item.profileVersion} · <span className="status-pill" data-state={item.active ? 'active' : 'inactive'}>{item.active ? 'Active' : 'Inactive'}</span></strong><span>{item.manufacturer} {item.model} · {item.wattage} W · {item.defaultBrightnessPct}%</span></li>)}</ul>}
            <form className="stack-form" onSubmit={submitProfile}><h3>Create profile</h3>
                <Label htmlFor="profile-code">Profile code</Label><Input id="profile-code" required maxLength={50} value={profileForm.profileCode} onChange={(event) => setProfileForm({ ...profileForm, profileCode: event.target.value })} />
                <Label htmlFor="profile-manufacturer">Manufacturer</Label><Input id="profile-manufacturer" required maxLength={80} value={profileForm.manufacturer} onChange={(event) => setProfileForm({ ...profileForm, manufacturer: event.target.value })} />
                <Label htmlFor="profile-model">Model</Label><Input id="profile-model" required maxLength={100} value={profileForm.model} onChange={(event) => setProfileForm({ ...profileForm, model: event.target.value })} />
              <div className="form-row"><label>Wattage<Input required type="number" step="any" min="0.01" value={profileForm.wattage} onChange={(event) => setProfileForm({ ...profileForm, wattage: event.target.value })} /></label><label>Default brightness %<Input required type="number" min="0" max="100" value={profileForm.defaultBrightnessPct} onChange={(event) => setProfileForm({ ...profileForm, defaultBrightnessPct: event.target.value })} /></label></div>
              <Label htmlFor="profile-controller">Controller type</Label><Input id="profile-controller" required maxLength={40} value={profileForm.controllerType} onChange={(event) => setProfileForm({ ...profileForm, controllerType: event.target.value })} />
              <Label htmlFor="profile-firmware">Firmware family</Label><Input id="profile-firmware" maxLength={50} value={profileForm.firmwareFamily} onChange={(event) => setProfileForm({ ...profileForm, firmwareFamily: event.target.value })} />
              <label>Configuration JSON<textarea rows={3} value={profileForm.configurationJson} onChange={(event) => setProfileForm({ ...profileForm, configurationJson: event.target.value })} /></label>
              <Button disabled={addProfile.isPending}>Create profile</Button>
            </form>
          </CardContent></Card>}
        </div>
      </div>
    </div>
  )
}
