import type { PropsWithChildren } from 'react'
import { Sidebar } from './sidebar'
import { Header } from './header'

export function AppShell({ children }: PropsWithChildren) {
  return (
    <div className="app-layout">
      <aside className="desktop-sidebar"><Sidebar /></aside>
      <div className="app-main">
        <Header />
        <main className="page-content">{children}</main>
      </div>
    </div>
  )
}
