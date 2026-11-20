import { LoaderCircle } from 'lucide-react'

type LoadingStateProps = {
  label: string
}

export function LoadingState({ label }: LoadingStateProps) {
  return <div className="loading-state" role="status" aria-live="polite" aria-atomic="true"><LoaderCircle aria-hidden="true" className="spin" />{label}</div>
}
