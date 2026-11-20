import { Button } from '@/components/ui/button'

type ErrorStateProps = {
  title: string
  message: string
  onRetry?: () => void
}

export function ErrorState({ title, message, onRetry }: ErrorStateProps) {
  return (
    <section className="error-state" role="alert" aria-atomic="true">
      <h2>{title}</h2>
      <p>{message}</p>
      {onRetry && <Button variant="secondary" onClick={onRetry}>Try again</Button>}
    </section>
  )
}
