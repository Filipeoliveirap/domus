import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../../forms/useAppForm'
import { categoriaSchema, type CategoriaFormInput, type CategoriaFormData } from '@/lib/validators'
import { categoriasService } from '@/services/financeiro/categoria.service'
import type { CategoriaRequest, CategoriaResponse } from '@/types/financeiro/categoria.type'
import type { ApiError } from '@/types/api.types'

interface UseCategoriaFormParams {
  categoriaId?: string
  categoriaInicial?: CategoriaResponse
  onSuccess?: () => void    
}

export function useCategoriaForm({ categoriaId, categoriaInicial, onSuccess }: UseCategoriaFormParams = {}) {
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!categoriaId

  // A11/rodada 3: se a categoria já está em uso, o salvamento pausa aqui pedindo confirmação
  // (atrito proporcional à consequência — sem lançamento associado, salva direto).
  const [confirmacaoPendente, setConfirmacaoPendente] = useState<{
    payload: CategoriaRequest
    totalMovimentacoes: number
  } | null>(null)

  const form = useAppForm<CategoriaFormInput, CategoriaFormData>({
    resolver: zodResolver(categoriaSchema),
    defaultValues: { nome: '', tipo: undefined },
    requiredFields: ['nome', 'tipo'],
  })

  const { reset } = form

  useEffect(() => {
    if (categoriaInicial) {
      reset({ nome: categoriaInicial.nome, tipo: categoriaInicial.tipo })
    }
  }, [categoriaInicial, reset])

  async function salvarEdicao(payload: CategoriaRequest) {
    await categoriasService.atualizar(categoriaId!, payload)
    invalidarCache(queryClient, 'categoria')
    queryClient.invalidateQueries({ queryKey: ['categoria', categoriaId] })
    notificar.sucesso('Categoria atualizada com sucesso!')
  }

  const onSubmit = async (data: CategoriaFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: CategoriaRequest = { nome: data.nome, tipo: data.tipo }

      if (ehEdicao) {
        // A11/rodada 3: só pede confirmação quando a categoria já está em uso — mudar uma
        // categoria sem lançamento associado é inofensivo e não deveria ter atrito.
        const total = await categoriasService.contarMovimentacoes(categoriaId!)
        if (total > 0) {
          setConfirmacaoPendente({ payload, totalMovimentacoes: total })
          return
        }
        await salvarEdicao(payload)
      } else {
        await categoriasService.criar(payload)
        invalidarCache(queryClient, 'categoria')
        notificar.sucesso('Categoria cadastrada com sucesso!')
      }
      onSuccess?.()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar categoria. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar categoria. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  async function confirmarAtualizacao() {
    if (!confirmacaoPendente) return
    setErroGeral(null)
    setIsLoading(true)
    try {
      await salvarEdicao(confirmacaoPendente.payload)
      setConfirmacaoPendente(null)
      onSuccess?.()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar categoria. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar categoria. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  function cancelarConfirmacao() {
    setConfirmacaoPendente(null)
  }

  return {
    ...form,
    onSubmit,
    erroGeral,
    isLoading,
    ehEdicao,
    confirmacaoPendente,
    confirmarAtualizacao,
    cancelarConfirmacao,
  }
}