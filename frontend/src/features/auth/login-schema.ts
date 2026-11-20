import { z } from 'zod'

export const loginSchema = z.object({
  email: z.email('Enter a valid email address.').max(254),
  password: z.string().min(1, 'Password is required.').max(1024, 'Password is too long.'),
})

export type LoginFormValues = z.infer<typeof loginSchema>
