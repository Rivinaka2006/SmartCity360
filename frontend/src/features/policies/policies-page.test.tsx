import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { AuthContext } from '@/features/auth/auth-state'
import { PoliciesPage } from './policies-page'

const policy = {
  id: 'policy-1',
  policyCode: 'NOISE_LIMIT',
  name: 'Noise limit',
  domain: 'NOISE',
  description: 'City noise policy.',
  status: 'ACTIVE',
  createdBy: 'admin',
  createdAt: '2026-11-09T00:00:00Z',
  updatedAt: '2026-11-09T00:00:00Z',
}

afterEach(() => vi.unstubAllGlobals())

describe('PoliciesPage', () => {
  it('presents backend policy status with a text label in list and detail', async () => {
    vi.stubGlobal('fetch', vi.fn((request: string) => {
      if (request.includes('/policies?')) return Promise.resolve(jsonResponse([policy]))
      if (request.includes('/versions?')) return Promise.resolve(jsonResponse([]))
      if (request.includes('/evaluations?')) return Promise.resolve(jsonResponse([]))
      if (request.endsWith('/policies/policy-1')) return Promise.resolve(jsonResponse(policy))
      return Promise.resolve(jsonResponse({ error: 'Unexpected request' }, 404))
    }))
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()

    render(
      <QueryClientProvider client={queryClient}>
        <AuthContext.Provider value={{
          status: 'authenticated',
          user: { id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] },
          error: null,
          login: vi.fn(),
          logout: vi.fn(),
        }}>
          <PoliciesPage />
        </AuthContext.Provider>
      </QueryClientProvider>,
    )

    const policyLink = await screen.findByRole('button', { name: /NOISE_LIMIT/ })
    expect(policyLink.querySelector('.status-pill')).toHaveTextContent('ACTIVE')
    expect(policyLink.querySelector('.status-pill')).toHaveAttribute('data-state', 'active')

    await user.click(policyLink)
    expect(await screen.findByRole('heading', { name: 'Noise limit' })).toBeVisible()
    const detailStatus = screen.getAllByText('ACTIVE').find((element) => element.classList.contains('status-pill'))
    expect(detailStatus).toBeInTheDocument()
  })

  it('disables policy mutations while preserving version and evaluation history for inactive policies', async () => {
    renderPolicyPage('INACTIVE')

    await userEvent.setup().click(await screen.findByRole('button', { name: /NOISE_LIMIT/ }))

    expect(await screen.findByRole('status')).toHaveTextContent(
      'This policy is deactivated. Its versions and history remain available, but it cannot be modified or reactivated through this workflow.',
    )
    expect(screen.getByRole('textbox', { name: 'DSL expression' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Create version' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Validate' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'Activate' })).toBeDisabled()
    expect(screen.getByText('HOUR >= 6 AND HOUR < 22')).toBeVisible()
    expect(screen.getByRole('heading', { name: 'Evaluation history' })).toBeVisible()
    expect(screen.getByText('SUCCESS').parentElement).toHaveTextContent('TRUE')
    expect(screen.getByText('Evaluation unavailable').nextElementSibling).toHaveTextContent(
      'Evaluation is unavailable while this policy is deactivated.',
    )
  })

  it.each(['ACTIVE', 'DRAFT'])('keeps permitted version controls enabled for %s policies', async (status) => {
    renderPolicyPage(status)

    await userEvent.setup().click(await screen.findByRole('button', { name: /NOISE_LIMIT/ }))

    expect(await screen.findByRole('heading', { name: 'Noise limit' })).toBeVisible()
    expect(screen.getByRole('textbox', { name: 'DSL expression' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Create version' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Validate' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Activate' })).toBeEnabled()
    expect(screen.queryByText(/cannot be modified or reactivated/)).not.toBeInTheDocument()
  })
})

function renderPolicyPage(status: string) {
  const selectedPolicy = { ...policy, status }
  const versions = [{
    id: 'version-1',
    policyId: selectedPolicy.id,
    versionNo: 1,
    dslVersion: '1',
    dslExpression: 'HOUR >= 6 AND HOUR < 22',
    valid: true,
    validationError: null,
    active: false,
    createdBy: 'admin',
    createdAt: '2026-11-09T00:00:00Z',
  }]
  const evaluations = [{
    id: 'evaluation-1',
    policyId: selectedPolicy.id,
    policyVersionId: 'version-1',
    versionNo: 1,
    evaluatedBy: 'admin',
    status: 'SUCCESS',
    result: true,
    errorCode: null,
    durationMs: 12,
    evaluatedAt: '2026-11-09T00:00:00Z',
    explanationJson: null,
  }]
  vi.stubGlobal('fetch', vi.fn((request: string) => {
    if (request.includes('/policies?')) return Promise.resolve(jsonResponse([selectedPolicy]))
    if (request.includes('/versions?')) return Promise.resolve(jsonResponse(versions))
    if (request.includes('/evaluations?')) return Promise.resolve(jsonResponse(evaluations))
    if (request.endsWith('/policies/policy-1')) return Promise.resolve(jsonResponse(selectedPolicy))
    return Promise.resolve(jsonResponse({ error: 'Unexpected request' }, 404))
  }))
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

  render(
    <QueryClientProvider client={queryClient}>
      <AuthContext.Provider value={{
        status: 'authenticated',
        user: { id: 'admin-1', displayName: 'Admin', email: 'admin@example.test', roles: ['ADMIN'] },
        error: null,
        login: vi.fn(),
        logout: vi.fn(),
      }}>
        <PoliciesPage />
      </AuthContext.Provider>
    </QueryClientProvider>,
  )
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}
