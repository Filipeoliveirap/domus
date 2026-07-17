'use client'

import { QueryClientProvider } from '@tanstack/react-query'
import { queryClient } from '@/lib/queryClient'
import { LimpezaSessaoLegada } from '@/components/auth/LimpezaSessaoLegada'

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <QueryClientProvider client={queryClient}>
      <LimpezaSessaoLegada />
      {children}
    </QueryClientProvider>
  )
}