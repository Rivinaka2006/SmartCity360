import * as Dialog from '@radix-ui/react-dialog'
import type { ComponentProps } from 'react'
import { cn } from '@/lib/utils'

export const Sheet = Dialog.Root
export const SheetTrigger = Dialog.Trigger
export const SheetClose = Dialog.Close

export function SheetContent({ className, children, ...props }: ComponentProps<typeof Dialog.Content>) {
  return (
    <Dialog.Portal>
      <Dialog.Overlay className="sheet-overlay" />
      <Dialog.Content className={cn('sheet-content', className)} {...props}>
        {children}
      </Dialog.Content>
    </Dialog.Portal>
  )
}

export const SheetTitle = Dialog.Title
export const SheetDescription = Dialog.Description
