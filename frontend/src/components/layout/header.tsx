import { ChevronDown, Menu, X } from 'lucide-react'
import { toast } from 'sonner'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Sheet, SheetContent, SheetDescription, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { useAuth } from '@/features/auth/use-auth'
import { Sidebar } from './sidebar'
import { ThemeToggle } from './theme-toggle'

export function Header() {
  const { user, logout } = useAuth()
  const [menuOpen, setMenuOpen] = useState(false)

  async function handleLogout() {
    try {
      await logout()
      toast.success('You have signed out.')
    } catch {
      toast.error('Sign out could not be completed. Please try again.')
    }
  }

  return (
    <header className="app-header">
      <div className="mobile-menu">
        <Sheet open={menuOpen} onOpenChange={setMenuOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" aria-label="Open navigation">
              {menuOpen ? <X aria-hidden="true" /> : <Menu aria-hidden="true" />}
            </Button>
          </SheetTrigger>
          <SheetContent>
            <SheetTitle className="sr-only">SmartCity360 navigation</SheetTitle>
            <SheetDescription className="sr-only">Choose a workspace page.</SheetDescription>
            <Sidebar onNavigate={() => setMenuOpen(false)} />
          </SheetContent>
        </Sheet>
      </div>
      <div className="header-context">
        <p className="eyebrow">City operations</p>
        <p className="header-greeting">Welcome, {user?.displayName}</p>
      </div>
      <div className="header-actions">
        <ThemeToggle />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="secondary" className="account-trigger" aria-label="Account menu">
              <span className="account-name">{user?.displayName}</span>
              <ChevronDown aria-hidden="true" size={16} />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <div className="account-details">
              <strong>{user?.displayName}</strong>
              <span>{user?.email}</span>
              <span>{user?.roles.join(', ')}</span>
            </div>
            <DropdownMenuSeparator className="dropdown-separator" />
            <DropdownMenuItem onSelect={() => void handleLogout()}>Sign out</DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}
