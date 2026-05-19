import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 1000 * 60 * 5,    // 5 minutos — dados ficam frescos
      retry: 1,                      // tenta 1 vez em caso de erro
      refetchOnWindowFocus: false,   // não rebusca ao focar a janela
    },
  },
})