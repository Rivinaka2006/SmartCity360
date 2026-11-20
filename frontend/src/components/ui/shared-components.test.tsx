import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { Button } from './button'
import { Card, CardContent, CardHeader, CardTitle } from './card'
import { Input } from './input'
import { Label } from './label'

describe('shared UI components', () => {
  it('keeps button variants, sizes, native types, and disabled semantics', () => {
    render(
      <form>
        <Button>Save</Button>
        <Button type="reset" variant="destructive" size="sm" disabled>Delete</Button>
      </form>,
    )

    const saveButton = screen.getByRole('button', { name: 'Save' })
    const deleteButton = screen.getByRole('button', { name: 'Delete' })

    expect(saveButton).toHaveClass('button', 'button-primary', 'button-md')
    expect(saveButton).toHaveProperty('type', 'submit')
    expect(deleteButton).toHaveClass('button-destructive', 'button-sm')
    expect(deleteButton).toHaveProperty('type', 'reset')
    expect(deleteButton).toBeDisabled()
  })

  it('composes the secondary button variant onto its child element', () => {
    render(
      <Button asChild variant="secondary">
        <a href="/app">Open workspace</a>
      </Button>,
    )

    expect(screen.getByRole('link', { name: 'Open workspace' })).toHaveClass('button', 'button-secondary')
  })

  it('preserves card content structure and title semantics', () => {
    const { container } = render(
      <Card>
        <CardHeader><CardTitle>Processing history</CardTitle></CardHeader>
        <CardContent>Latest meter records</CardContent>
      </Card>,
    )

    expect(container.querySelector('section.card')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Processing history', level: 2 })).toBeVisible()
    expect(screen.getByText('Latest meter records')).toBeVisible()
  })

  it('keeps input attributes and explicit label association', () => {
    render(
      <div>
        <Label htmlFor="meter-code">Meter code</Label>
        <Input id="meter-code" aria-invalid="true" disabled />
      </div>,
    )

    const input = screen.getByLabelText('Meter code')
    expect(input).toHaveClass('input')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toBeDisabled()
  })
})
