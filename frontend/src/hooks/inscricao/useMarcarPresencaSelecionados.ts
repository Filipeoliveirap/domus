'use client'

import axios from 'axios'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { inscricoesService } from '@/services/inscricao.service'
import type { ApiError } from '@/types/api.types'
import type { ListaInscritosResponse } from '@/types/inscricao.type'

export interface ItemSelecionado {
  id: string
  tipo: 'inscricao'
}

// Sucesso não reinvalida ['inscricoes','lista'], senão os botões piscam de volta pra "Marcar presença".
export function useMarcarPresencaSelecionados(eventoId: string) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: (itens: ItemSelecionado[]) =>
      Promise.all(itens.map((item) =>
        inscricoesService.marcarPresencaInscricao(eventoId, item.id, true),
      )),
    onMutate: async (itens) => {
      await queryClient.cancelQueries({ queryKey: ['inscricoes', 'lista', eventoId] })
      const anteriores = queryClient.getQueriesData<ListaInscritosResponse>({
        queryKey: ['inscricoes', 'lista', eventoId],
      })
      const idsInscricao = new Set(itens.map((i) => i.id))
      queryClient.setQueriesData<ListaInscritosResponse>(
        { queryKey: ['inscricoes', 'lista', eventoId] },
        (atual) => atual && {
          ...atual,
          inscritos: {
            ...atual.inscritos,
            content: atual.inscritos.content.map((i) => ({
              ...i,
              compareceu: idsInscricao.has(i.id) ? true : i.compareceu,
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
    onSuccess: (_data, itens) => {
      queryClient.invalidateQueries({ queryKey: ['relatorio-evento', eventoId] })
      notificar.sucesso(
        itens.length === 1 ? 'Presença marcada para 1 pessoa.' : `Presença marcada para ${itens.length} pessoas.`,
      )
    },
  })
}
