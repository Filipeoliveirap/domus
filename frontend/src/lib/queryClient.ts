import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 30s (era 5min): invalidação explícita cobre o que ESTE usuário muda; staleTime curto + refetchOnWindowFocus cobre mudanças de outro admin/dispositivo.
      staleTime: 1000 * 30,
      retry: 1,
      refetchOnWindowFocus: true,
      refetchOnReconnect: true,
    },
  },
})