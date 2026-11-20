import { Activity, Building2, ClipboardList, Lightbulb, Map, Users, Zap, type LucideIcon } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { availableNavigation } from '@/routes/route-policy'
import { useAuth } from '@/features/auth/use-auth'

const routeIcons: Record<string, LucideIcon> = {
  dashboard: Activity,
  zones: Map,
  complaints: ClipboardList,
  streetlights: Lightbulb,
  energy: Zap,
  policies: ClipboardList,
  users: Users,
}

type SidebarProps = {
  onNavigate?: () => void
}

export function Sidebar({ onNavigate }: SidebarProps) {
  const { user } = useAuth()
  const navigation = availableNavigation(user?.roles ?? [])

  return (
    <div className="sidebar-content">
      <NavLink to="/app" className="sidebar-brand" onClick={onNavigate} aria-label="SmartCity360 home">
        <span className="brand-mark"><Building2 aria-hidden="true" /></span>
        <span className="brand-copy">
          <span className="brand-name">SmartCity<span className="brand-accent">360</span></span>
          <span className="brand-caption">MIDNIGHT COMMAND</span>
        </span>
      </NavLink>
      <p className="sidebar-label">Workspace</p>
      <nav aria-label="Main navigation" className="sidebar-nav">
        {navigation.map((route) => {
          const Icon = routeIcons[route.id]
          return (
            <NavLink
              key={route.id}
              to={route.path}
              end={route.id === 'dashboard'}
              onClick={onNavigate}
              className={({ isActive }) => `nav-link${isActive ? ' nav-link-active' : ''}`}
            >
              <Icon aria-hidden="true" size={18} />
              <span>{route.label}</span>
            </NavLink>
          )
        })}
      </nav>
      <div className="sidebar-footer">Civic services · Secure workspace</div>
    </div>
  )
}
