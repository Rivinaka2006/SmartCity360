import { zodResolver } from '@hookform/resolvers/zod'
import { Building2, LoaderCircle } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { ApiError } from '@/lib/api/api-error'
import { loginSchema, type LoginFormValues } from './login-schema'
import { useAuth } from './use-auth'

export function LoginPage() {
  const auth = useAuth()
  const [submissionError, setSubmissionError] = useState<string>()
  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  const submit = form.handleSubmit(async (values) => {
    setSubmissionError(undefined)
    try {
      await auth.login(values)
    } catch (error) {
      setSubmissionError(
        error instanceof ApiError && error.status === 401
          ? 'Email or password is incorrect.'
          : 'Sign in could not be completed. Please try again.',
      )
    }
  })

  return (
    <main className="login-page">
      <section aria-labelledby="login-title" className="login-card login-panel">
        <div className="brand-mark" aria-hidden="true"><Building2 /></div>
        <p className="eyebrow">SmartCity360</p>
        <h1 id="login-title">Sign in to city operations</h1>
        <p className="muted">Use your authorized SmartCity360 account.</p>

        <form onSubmit={submit} noValidate>
          <div className="field">
            <Label htmlFor="email">Email address</Label>
            <Input
              id="email"
              type="email"
              autoComplete="username"
              aria-invalid={Boolean(form.formState.errors.email)}
              aria-describedby={form.formState.errors.email ? 'email-error' : undefined}
              {...form.register('email')}
            />
            {form.formState.errors.email && <p id="email-error" className="field-error">{form.formState.errors.email.message}</p>}
          </div>
          <div className="field">
            <Label htmlFor="password">Password</Label>
            <Input
              id="password"
              type="password"
              autoComplete="current-password"
              aria-invalid={Boolean(form.formState.errors.password)}
              aria-describedby={form.formState.errors.password ? 'password-error' : undefined}
              {...form.register('password')}
            />
            {form.formState.errors.password && <p id="password-error" className="field-error">{form.formState.errors.password.message}</p>}
          </div>
          {submissionError && <p role="alert" className="form-error">{submissionError}</p>}
          <Button type="submit" aria-busy={form.formState.isSubmitting || undefined} disabled={form.formState.isSubmitting || auth.status === 'loading'}>
            {form.formState.isSubmitting && <LoaderCircle aria-hidden="true" className="spin" />}
            Sign in
          </Button>
        </form>
      </section>
    </main>
  )
}
