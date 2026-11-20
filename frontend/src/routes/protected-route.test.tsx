import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { AuthContext, type AuthContextValue } from '@/features/auth/auth-state'
import { NotFoundView } from '@/components/common/not-found-view'
import { ProtectedRoute } from './protected-route'

const user = {
  id: '0f6ae02d-54cc-44ec-909f-5758c3b30bd9',
  displayName: 'Asha Perera',
  email: 'asha@example.test',
  roles: ['RESIDENT'] as const,
}

function LoginTarget() {
  const location = useLocation()
  const state = location.state as { returnTo?: string } | null
  return <p>Login target: {state?.returnTo ?? 'none'}</p>
}

function renderProtected(auth: AuthContextValue, initialPath = '/app/zones') {
  return render(
    <AuthContext.Provider value={auth}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<LoginTarget />} />
          <Route path="/app/zones" element={(
            <ProtectedRoute allowedRoles={['ADMIN', 'CITY_MANAGER', 'SUPERVISOR', 'LOCAL_STAFF']}>
              <p>Zone management</p>
            </ProtectedRoute>
          )} />
          <Route path="*" element={<NotFoundView />} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>,
  )
}

const actions = { login: vi.fn(), logout: vi.fn() }

describe('ProtectedRoute', () => {
  it('does not render protected content while session initialization is unresolved', () => {
    renderProtected({ status: 'loading', user: null, error: null, ...actions })

    expect(screen.getByRole('status')).toHaveTextContent('Checking your session')
    expect(screen.queryByText('Zone management')).not.toBeInTheDocument()
  })

  it('redirects an anonymous caller to login with a local return path', () => {
    renderProtected({ status: 'anonymous', user: null, error: null, ...actions })

    expect(screen.getByText('Login target: /app/zones')).toBeVisible()
  })

  it('renders an unauthorized view for an authenticated caller without a permitted role', () => {
    renderProtected({ status: 'authenticated', user: { ...user, roles: ['RESIDENT'] }, error: null, ...actions })

    expect(screen.getByRole('heading', { name: 'Access not available' })).toBeVisible()
    expect(screen.queryByText('Zone management')).not.toBeInTheDocument()
  })

  it('renders protected content for a permitted database-derived role', () => {
    renderProtected({ status: 'authenticated', user: { ...user, roles: ['SUPERVISOR'] }, error: null, ...actions })

    expect(screen.getByText('Zone management')).toBeVisible()
  })

  it('renders a not-found view for an unknown application URL', () => {
    renderProtected({ status: 'authenticated', user: { ...user, roles: ['ADMIN'] }, error: null, ...actions }, '/app/unknown')

    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeVisible()
  })
})
