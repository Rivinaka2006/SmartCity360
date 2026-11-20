import { Link } from 'react-router-dom'

export function UnauthorizedView() {
  return (
    <main className="centered-state">
      <p className="eyebrow">Permission required</p>
      <h1>Access not available</h1>
      <p className="muted">Your current SmartCity360 roles do not allow access to this module.</p>
      <Link to="/app">Return to dashboard</Link>
    </main>
  )
}
