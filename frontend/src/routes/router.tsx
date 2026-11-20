import { createBrowserRouter, Navigate } from 'react-router-dom'
import { NotFoundView } from '@/components/common/not-found-view'
import { ComplaintsPage } from '@/features/complaints/complaints-page'
import { DashboardPage } from '@/features/dashboard/dashboard-page'
import { EnergyPage } from '@/features/energy/energy-page'
import { PoliciesPage } from '@/features/policies/policies-page'
import { StreetlightsPage } from '@/features/streetlights/streetlights-page'
import { ZonesPage } from '@/features/zones/zones-page'
import { UsersPage } from '@/features/users/users-page'
import { appRoutes } from './route-policy'
import { LoginRoute, ProtectedApplication, RoleRoute } from './route-elements'

const moduleViews = {
  zones: <ZonesPage />,
  complaints: <ComplaintsPage />,
  streetlights: <StreetlightsPage />,
  energy: <EnergyPage />,
  policies: <PoliciesPage />,
  users: <UsersPage />,
} as const

export const router = createBrowserRouter([
  { path: '/', element: <Navigate to="/app" replace /> },
  { path: '/login', element: <LoginRoute /> },
  {
    path: '/app',
    element: <ProtectedApplication />,
    children: [
      ...appRoutes.map((route) => ({
        index: route.id === 'dashboard' ? true : undefined,
        path: route.id === 'dashboard' ? undefined : route.path.slice('/app/'.length),
        element: (
          <RoleRoute roles={route.allowedRoles}>
            {route.id === 'dashboard' ? <DashboardPage /> : moduleViews[route.id]}
          </RoleRoute>
        ),
      })),
    ],
  },
  { path: '*', element: <NotFoundView /> },
])
