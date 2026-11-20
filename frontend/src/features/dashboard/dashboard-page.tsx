import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Activity, AlertTriangle, Building2, FileWarning, Lightbulb, MapPinned, Power, ShieldCheck, Zap } from 'lucide-react'
import { EmptyState } from '@/components/common/empty-state'
import { ErrorState } from '@/components/common/error-state'
import { LoadingState } from '@/components/common/loading-state'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { useAuth } from '@/features/auth/use-auth'
import { availableNavigation } from '@/routes/route-policy'
import { fetchDashboardSummary, dashboardQueryKey } from './dashboard-api'
import { fetchHealth, healthQueryKey } from './health-api'

function formatDateTime(value: string) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) + ' UTC'
}

function Metric({ label, value, icon: Icon, tone = '' }: { label: string; value: string | number; icon: typeof Activity; tone?: string }) {
  return (
    <dl className={`dashboard-metric ${tone}`}>
      <dt>{label}</dt>
      <dd><span className="metric-icon"><Icon aria-hidden="true" /></span><span>{value}</span></dd>
    </dl>
  )
}

function SummaryRow({ label, value }: { label: string; value: number }) {
  return <div className="summary-row"><span>{label}</span><strong>{value}</strong></div>
}

export function DashboardPage() {
  const { user } = useAuth()
  const isResidentOnly = Boolean(user?.roles.includes('RESIDENT')
    && !user.roles.some((role) => role === 'ADMIN' || role === 'CITY_MANAGER' || role === 'SUPERVISOR' || role === 'LOCAL_STAFF'))
  const health = useQuery({ queryKey: healthQueryKey, queryFn: ({ signal }) => fetchHealth(signal) })
  const summary = useQuery({ queryKey: dashboardQueryKey, queryFn: ({ signal }) => fetchDashboardSummary(signal), enabled: !isResidentOnly })
  const modules = availableNavigation(user?.roles ?? []).filter((route) => route.id !== 'dashboard')

  if (isResidentOnly) {
    return (
      <div className="dashboard-page">
        <section className="page-heading dashboard-heading">
          <p className="eyebrow">Resident workspace</p>
          <h1>Welcome to SmartCity360</h1>
          <p className="muted">Submit and follow your community service requests from one secure place.</p>
        </section>
        <div className="dashboard-grid dashboard-account-grid">
          <Card><CardHeader><CardTitle>Your complaint services</CardTitle></CardHeader><CardContent className="feature-stack"><p>Report an issue at an active city location and follow updates to your own requests.</p><Button asChild><Link to="/app/complaints">My complaints</Link></Button></CardContent></Card>
          <Card><CardHeader><CardTitle>Your account</CardTitle></CardHeader><CardContent><p className="profile-name">{user?.displayName}</p><p className="muted">{user?.email}</p></CardContent></Card>
        </div>
        <Card><CardHeader><CardTitle>Backend connection</CardTitle></CardHeader><CardContent aria-live="polite">
          {health.isPending && <LoadingState label="Checking backend health" />}
          {health.isError && <ErrorState title="Backend unavailable" message="The health service could not be reached." onRetry={() => void health.refetch()} />}
          {health.data && <div className="health-result"><span className={`health-indicator${health.data.status.toLowerCase() === 'ok' || health.data.status.toLowerCase() === 'up' ? ' health-indicator-good' : ''}`} aria-hidden="true" /><span className="health-service">{health.data.service}</span><span className="health-status">{health.data.status}</span></div>}
        </CardContent></Card>
      </div>
    )
  }

  return (
    <div className="dashboard-page">
      <section className="page-heading dashboard-heading">
        <p className="eyebrow">Operations overview</p>
        <h1>Operations command center</h1>
        <p className="muted">City infrastructure status, service demand, and governance posture in one view.</p>
      </section>
      {summary.isPending && <Card><CardContent><LoadingState label="Loading operations summary" /></CardContent></Card>}
      {summary.isError && <ErrorState title="Operations summary unavailable" message="The operations summary could not be reached." onRetry={() => void summary.refetch()} />}
      {summary.data && <>
      <div className="dashboard-kpis" role="group" aria-label="Key operational indicators">
        <Metric label="Active zones" value={summary.data.zones.total} icon={MapPinned} />
        <Metric label="Buildings" value={summary.data.zones.buildings} icon={Building2} />
        <Metric label="Open complaints" value={summary.data.complaints.open + summary.data.complaints.escalated} icon={FileWarning} tone="metric-warning" />
        <Metric label="Lights online" value={summary.data.streetlights.on} icon={Lightbulb} tone="metric-success" />
      </div>
      <div className="dashboard-grid dashboard-panels">
        <Card>
          <CardHeader><CardTitle><Power aria-hidden="true" /> Zone power state</CardTitle></CardHeader>
          <CardContent>
            {summary.data.zones.buildings === 0 ? <EmptyState title="No buildings yet" message="Power state coverage will appear when active building zones are registered." /> : <div className="summary-rows">
              <SummaryRow label="Powered on" value={summary.data.power.on} />
              <SummaryRow label="Powered off" value={summary.data.power.off} />
              <SummaryRow label="State unavailable" value={summary.data.power.unknown} />
            </div>}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle><AlertTriangle aria-hidden="true" /> Complaint workload</CardTitle></CardHeader>
          <CardContent>
            {summary.data.complaints.total === 0 ? <EmptyState title="No complaints recorded" message="The complaint queue is currently empty." /> : <div className="summary-rows">
              <SummaryRow label="Open" value={summary.data.complaints.open} />
              <SummaryRow label="In progress" value={summary.data.complaints.inProgress} />
              <SummaryRow label="Escalated" value={summary.data.complaints.escalated} />
              <SummaryRow label="Resolved" value={summary.data.complaints.resolved} />
              <SummaryRow label="Rejected" value={summary.data.complaints.rejected} />
            </div>}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle><Lightbulb aria-hidden="true" /> Streetlight network</CardTitle></CardHeader>
          <CardContent>
            {summary.data.streetlights.total === 0 ? <EmptyState title="No streetlights registered" message="Streetlight operational status will appear after assets are registered." /> : <div className="summary-rows">
              <SummaryRow label="On" value={summary.data.streetlights.on} />
              <SummaryRow label="Off" value={summary.data.streetlights.off} />
              <SummaryRow label="Fault" value={summary.data.streetlights.fault} />
              <SummaryRow label="Maintenance" value={summary.data.streetlights.maintenance} />
            </div>}
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle><Zap aria-hidden="true" /> Energy consumption</CardTitle></CardHeader>
          <CardContent>
            <p className="energy-value">{summary.data.energy.consumptionKwh === null
              ? 'No readings'
              : typeof summary.data.energy.consumptionKwh === 'number' && Number.isFinite(summary.data.energy.consumptionKwh)
                ? `${summary.data.energy.consumptionKwh.toFixed(2)} kWh`
                : 'Unavailable'}</p>
            <p className="muted">Previous 24 hours · {summary.data.energy.readingCount} readings</p>
            {summary.data.energy.consumptionKwh === null && <p className="empty-inline">No energy measurements in the reporting period.</p>}
            <p className="report-period">{formatDateTime(summary.data.energy.from)} – {formatDateTime(summary.data.energy.to)}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle><ShieldCheck aria-hidden="true" /> Policy posture</CardTitle></CardHeader>
          <CardContent>
            {summary.data.policies.total === 0 ? <EmptyState title="No policies configured" message="Policy status will appear when governance policies are created." /> : <div className="summary-rows">
              <SummaryRow label="Active" value={summary.data.policies.active} />
              <SummaryRow label="Draft" value={summary.data.policies.draft} />
              <SummaryRow label="Inactive" value={summary.data.policies.inactive} />
            </div>}
          </CardContent>
        </Card>
      </div>
      <p className="dashboard-as-of"><Activity aria-hidden="true" /> Data observed {formatDateTime(summary.data.asOf)} · not an atomic database snapshot</p>
      </>}
      <div className="dashboard-grid dashboard-account-grid">
        <Card>
          <CardHeader><CardTitle>Your account</CardTitle></CardHeader>
          <CardContent>
            <p className="profile-name">{user?.displayName}</p>
            <p className="muted">{user?.email}</p>
            <div className="role-list" aria-label="Your roles">
              {user?.roles.map((role) => <span className="role-badge" key={role}>{role.replaceAll('_', ' ')}</span>)}
            </div>
          </CardContent>
        </Card>
        <Card>
          <CardHeader><CardTitle>Backend connection</CardTitle></CardHeader>
          <CardContent aria-live="polite">
            {health.isPending && <LoadingState label="Checking backend health" />}
            {health.isError && <ErrorState title="Backend unavailable" message="The health service could not be reached." onRetry={() => void health.refetch()} />}
            {health.data && (
              <div className="health-result">
                <span className={`health-indicator${health.data.status.toLowerCase() === 'ok' || health.data.status.toLowerCase() === 'up' ? ' health-indicator-good' : ''}`} aria-hidden="true" />
                <span className="health-service">{health.data.service}</span>
                <span className="health-status">{health.data.status}</span>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
      <section className="module-section" aria-labelledby="available-modules">
        <div className="section-heading">
          <div><p className="eyebrow">Workspace</p><h2 id="available-modules">Available modules</h2></div>
        </div>
        <div className="module-links">
          {modules.map((module) => (
            <Button key={module.id} variant="secondary" asChild><Link to={module.path}>{module.label}</Link></Button>
          ))}
        </div>
      </section>
    </div>
  )
}
