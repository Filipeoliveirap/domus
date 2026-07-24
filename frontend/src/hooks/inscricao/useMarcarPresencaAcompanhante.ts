'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { ListaInscritosResponse } from '@/types/inscricao.type'

/**
 * Mesma correção pontual de `useMarcarPresencaInscricao`, só que para UM convidado — mesma
 * atualização otimista e mesmo motivo pra NÃO reinvalidar `['inscricoes','lista']` no
 * sucesso (ver Javadoc lá).
 */
export function useMarcarPresencaAcompanhante(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ acompanhanteId, compareceu }: { acompanhanteId: string; compareceu: boolean }) =>
      inscricoesService.marcarPresencaAcompanhante(eventoId, acompanhanteId, compareceu),
    onMutate: async ({ acompanhanteId, compareceu }) => {
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
              acompanhantes: i.acompanhantes.map((a) =>
                a.id === acompanhanteId ? { ...a, compareceu } : a),
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
    },
  })
}
