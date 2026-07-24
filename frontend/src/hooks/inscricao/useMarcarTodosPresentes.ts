'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { ListaInscritosResponse } from '@/types/inscricao.type'

/**
 * Botão "marcar todos vieram" — presença em lote de todo inscrito confirmado + acompanhantes.
 * Atualização otimista (mesmo motivo do botão individual, ver Javadoc de
 * `useMarcarPresencaInscricao`): o sucesso NÃO reinvalida `['inscricoes','lista']` — fazer
 * isso disparava um refetch em cima do próprio patch otimista e os botões "piscavam" de
 * volta pra "Marcar presença" por um instante antes de assentar.
 */
export function useMarcarTodosPresentes(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: () => inscricoesService.marcarTodosPresentes(eventoId),
    onMutate: async () => {
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
            content: atual.inscritos.content.map((i) => ({
              ...i,
              compareceu: true,
              acompanhantes: i.acompanhantes.map((a) => ({ ...a, compareceu: true })),
            })),
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
      notificar.sucesso('Presença marcada para todos os inscritos.')
    },
  })
}
