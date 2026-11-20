import * as DropdownMenuPrimitive from '@radix-ui/react-dropdown-menu'
import type { ComponentProps } from 'react'

export const DropdownMenu = DropdownMenuPrimitive.Root
export const DropdownMenuTrigger = DropdownMenuPrimitive.Trigger

export function DropdownMenuContent({ className = '', ...props }: ComponentProps<typeof DropdownMenuPrimitive.Content>) {
  return <DropdownMenuPrimitive.Portal><DropdownMenuPrimitive.Content className={`dropdown-content ${className}`} sideOffset={8} {...props} /></DropdownMenuPrimitive.Portal>
}

export function DropdownMenuItem({ className = '', ...props }: ComponentProps<typeof DropdownMenuPrimitive.Item>) {
  return <DropdownMenuPrimitive.Item className={`dropdown-item ${className}`} {...props} />
}

export const DropdownMenuSeparator = DropdownMenuPrimitive.Separator
