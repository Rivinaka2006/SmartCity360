import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { EmptyState } from './empty-state'
import { ErrorState } from './error-state'
import { LoadingState } from './loading-state'

describe('shared feedback components', () => {
  it('announces loading labels politely without exposing the decorative icon', () => {
    render(<LoadingState label="Loading records" />)

    const status = screen.getByRole('status')
    expect(status).toHaveTextContent('Loading records')
    expect(status).toHaveAttribute('aria-live', 'polite')
    expect(status).toHaveAttribute('aria-atomic', 'true')
    expect(status.querySelector('svg')).toHaveAttribute('aria-hidden', 'true')
  })

  it('presents empty states with their existing title and explanation', () => {
    render(<EmptyState title="No records" message="Records will appear here when available." />)

    expect(screen.getByRole('heading', { name: 'No records', level: 2 })).toBeVisible()
    expect(screen.getByText('Records will appear here when available.')).toBeVisible()
  })

  it('announces errors and invokes the existing retry action', () => {
    const onRetry = vi.fn()
    render(<ErrorState title="Records unavailable" message="The records could not be loaded." onRetry={onRetry} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveAttribute('aria-atomic', 'true')
    expect(alert).toHaveTextContent('Records unavailable')
    expect(alert).toHaveTextContent('The records could not be loaded.')

    fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('does not render a retry action when none is provided', () => {
    render(<ErrorState title="Unavailable" message="Try again later." />)

    expect(screen.queryByRole('button', { name: 'Try again' })).not.toBeInTheDocument()
  })
})
