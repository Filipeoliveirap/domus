import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useFieldArray } from 'react-hook-form'
import { useAppForm } from '../../forms/useAppForm'
import { movimentacaoSchema, type MovimentacaoFormInput, type MovimentacaoFormData } from '@/lib/validators'
import { movimentacoesService } from '@/services/financeiro/movimentacao.service'
import type { MovimentacaoRequest, MovimentacaoResponse } from '@/types/financeiro/movimentacao.type'
import type { ApiError } from '@/types/api.types'
import { useRouter } from 'next/navigation'

interface UseMovimentacaoFormParams {
  movimentacaoId?: string
  movimentacaoInicial?: MovimentacaoResponse
  onSuccess?: () => void
}

export function useMovimentacaoForm({ movimentacaoId, movimentacaoInicial, onSuccess }: UseMovimentacaoFormParams = {}) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!movimentacaoId
  const router = useRouter()

  const form = useAppForm<MovimentacaoFormInput, MovimentacaoFormData>({
    resolver: zodResolver(movimentacaoSchema),
    defaultValues: {
      tipo: undefined,
      valor: '',
      categoriaId: '',
      dataMovimentacao: '',
      contribuintes: [],
      descricao: '',
    },
    requiredFields: ['tipo', 'valor', 'categoriaId', 'dataMovimentacao'],
  })

  const { reset, control } = form
  const contribuintesArray = useFieldArray({ control, name: 'contribuintes' })

  useEffect(() => {
    if (movimentacaoInicial) {
      reset({
        tipo: movimentacaoInicial.tipo,
        valor: String(movimentacaoInicial.valor),
        categoriaId: movimentacaoInicial.categoriaId,
        dataMovimentacao: movimentacaoInicial.dataMovimentacao.split('T')[0],
        // pessoaId nulo (pessoa excluída definitivamente, sem nomeExterno) vira campo vazio
        // — precisa escolher alguém de novo pra editar essa linha, não dá pra recuperar
        // quem era. nomeExterno preenchido é o caso "de fora", editável como texto livre.
        contribuintes: movimentacaoInicial.contribuintes.map((c) => ({
          pessoaId: c.pessoaId ?? '',
          nomeExterno: c.nomeExterno ?? '',
          valor: String(c.valor),
        })),
        descricao: movimentacaoInicial.descricao ?? '',
      })
    }
  }, [movimentacaoInicial, reset])

  const onSubmit = async (data: MovimentacaoFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: MovimentacaoRequest = {
        tipo: data.tipo,
        valor: data.valor,
        categoriaId: data.categoriaId,
        dataMovimentacao: data.dataMovimentacao,
        // String vazia não é válida como UUID no backend — precisa virar null.
        contribuintes: data.contribuintes.map((c) => ({
          pessoaId: c.pessoaId || null,
          nomeExterno: c.nomeExterno || null,
          valor: c.valor,
        })),
        descricao: data.descricao || undefined,
      }

      if (ehEdicao) {
        await movimentacoesService.atualizar(movimentacaoId!, payload)
        invalidarCache(queryClient, 'movimentacao')
        queryClient.invalidateQueries({ queryKey: ['movimentacao', movimentacaoId] })
        notificar.sucesso('Movimentação atualizada com sucesso!')
      } else {
        await movimentacoesService.criar(payload)
        invalidarCache(queryClient, 'movimentacao')
        notificar.sucesso('Movimentação registrada com sucesso!')
      }
      onSuccess?.()
      router.back()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'TIPO_INCOMPATIVEL') {
          form.setError('categoriaId', { type: 'server', message: e.message })
          return
        }
        if (e?.error === 'VALOR_CONTRIBUINTES_DIVERGENTE' || e?.error === 'CONTRIBUINTE_DUPLICADO') {
          form.setError('contribuintes', { type: 'server', message: e.message })
          return
        }
        setErroGeral(e?.message ?? 'Erro ao salvar movimentação. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar movimentação. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { ...form, contribuintesArray, onSubmit, erroGeral, isLoading, ehEdicao }
}
