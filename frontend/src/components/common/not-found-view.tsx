import { Link } from 'react-router-dom'

export function NotFoundView() {
  return (
    <main className="centered-state">
      <p className="eyebrow">404</p>
      <h1>Page not found</h1>
      <p className="muted">The requested SmartCity360 page does not exist.</p>
      <Link to="/app">Return to dashboard</Link>
    </main>
  )
}
