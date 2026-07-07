import { useState, useEffect } from 'react'
import axios from 'axios'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
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

  const onSubmit = async (data: CategoriaFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: CategoriaRequest = { nome: data.nome, tipo: data.tipo }

      if (ehEdicao) {
        await categoriasService.atualizar(categoriaId!, payload)
        queryClient.invalidateQueries({ queryKey: ['categorias'] })
        queryClient.invalidateQueries({ queryKey: ['categoria', categoriaId] })
        toast.success('Categoria atualizada com sucesso!')
      } else {
        await categoriasService.criar(payload)
        queryClient.invalidateQueries({ queryKey: ['categorias'] })
        toast.success('Categoria cadastrada com sucesso!')
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

  return { ...form, onSubmit, erroGeral, isLoading, ehEdicao }
}