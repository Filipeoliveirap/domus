import { QueryClient } from '@tanstack/react-query'

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      /*
       * 30s (era 5min). Duas camadas mantêm a tela fresca:
       *
       * 1. INVALIDAÇÃO EXPLÍCITA (lib/cacheInvalidacao.ts) — cobre o que ESTE usuário muda.
       *    É instantânea e precisa; é o que conserta "cadastrei um evento e o início não
       *    mostrou".
       * 2. staleTime curto + refetchOnWindowFocus — rede de segurança para o que MUDOU FORA
       *    daqui: outro admin cadastrando, ou você mesmo em outro dispositivo. Nenhuma
       *    invalidação local enxerga isso.
       *
       * 5min era longo demais para dado que uma equipe edita junto: dava para olhar uma tela
       * por vários minutos vendo número desatualizado sem nenhum sinal disso.
       */
      staleTime: 1000 * 30,
      retry: 1,
      // Voltar para a aba revalida o que estiver velho. Com staleTime de 30s isso é barato:
      // alternar de aba rapidamente não dispara requisição nenhuma.
      refetchOnWindowFocus: true,
      // Reconectou depois de cair a internet? O que está na tela pode ter envelhecido.
      refetchOnReconnect: true,
    },
  },
})