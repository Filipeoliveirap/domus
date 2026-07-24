'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { ListaInscritosResponse } from '@/types/inscricao.type'

/**
 * Corrige a exceção de UM inscrito específico (o botão individual da lista) — depois de
 * um "marcar todos", ou independentemente dele.
 *
 * Atualização otimista: o botão precisa alternar (marcar/desmarcar) na hora do clique, sem
 * esperar a resposta do servidor. Por isso o sucesso NÃO reinvalida `['inscricoes','lista']`
 * — isso disparava um refetch em cima do próprio patch otimista e piscava (o valor sumia e
 * voltava por uma fração de segundo, e um clique rápido nesse meio-tempo cancelava o que
 * tinha acabado de marcar). `compareceu` não afeta vagas/elegibilidade, então só o relatório
 * (cards de presença) precisa mesmo recarregar.
 */
export function useMarcarPresencaInscricao(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ inscricaoId, compareceu }: { inscricaoId: string; compareceu: boolean }) =>
      inscricoesService.marcarPresencaInscricao(eventoId, inscricaoId, compareceu),
    onMutate: async ({ inscricaoId, compareceu }) => {
      await queryClient.cancelQueries({ queryKey: ['inscricoes', 'lista', eventoId] })
      const anteriores = queryClient.getQueriesData<ListaInscritosResponse>({
        queryKey: ['inscricoes', 'lista', eventoId],
      })
      queryClient.setQueriesData<ListaInscritosResponse>(
        { queryKey: ['inscricoes', 'lista', eventoId] },
        (atual) => atual && {
          ...atual,
          inscritos: {
            ...atual.inscritos,
            content: atual.inscritos.content.map((i) =>
              i.id === inscricaoId ? { ...i, compareceu } : i),
          },
        },
      )
      return { anteriores }
    },
    onError: (error: unknown, _vars, contexto) => {
      contexto?.anteriores.forEach(([queryKey, dados]) => queryClient.setQueryData(queryKey, dados))
      const mensagem = axios.isAxiosError<ApiError>(error) ? error.response?.data?.message : undefined
      notificar.erro('Não foi possível marcar presença', mensagem ?? 'Tente novamente.')
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['relatorio-evento', eventoId] })
    },
  })
}
