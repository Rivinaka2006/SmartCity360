import { Component, type ErrorInfo, type PropsWithChildren, type ReactNode } from 'react'
import { ErrorState } from './error-state'

type State = { hasError: boolean }

export class AppErrorBoundary extends Component<PropsWithChildren, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Application rendering failed', error, info.componentStack)
  }

  render(): ReactNode {
    if (this.state.hasError) {
      return <main className="page-content"><ErrorState title="Something went wrong" message="The page could not be displayed. Reload the page to try again." /></main>
    }
    return this.props.children
  }
}
