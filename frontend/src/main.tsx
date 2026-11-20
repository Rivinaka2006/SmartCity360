import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { AppErrorBoundary } from '@/components/common/app-error-boundary'
import { AppProviders } from '@/app/providers'
import { router } from '@/routes/router'
import '@/styles/index.css'

const rootElement = document.getElementById('root')

if (!rootElement) throw new Error('The application root element is missing.')

createRoot(rootElement).render(
  <StrictMode>
    <AppErrorBoundary>
      <AppProviders>
        <RouterProvider router={router} />
      </AppProviders>
    </AppErrorBoundary>
  </StrictMode>,
)
