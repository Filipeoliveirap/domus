import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../forms/useAppForm'
import { membroSchema, type MembroFormInput, type MembroFormData } from '@/lib/validators'
import { membrosService } from '@/services/membro.service'
import { formatarTelefone, formatarCep } from '@/lib/masks'
import type { MembroRequest, MembroResponse } from '@/types/membro.type'
import type { ApiError } from '@/types/api.types'

interface UseMembroFormParams {
  membroId?: string                 
  membroInicial?: MembroResponse  
}

export function useMembroForm({ membroId, membroInicial }: UseMembroFormParams = {}) {
  const router = useRouter()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!membroId

  const form = useAppForm<MembroFormInput, MembroFormData>({
    resolver: zodResolver(membroSchema),
    defaultValues: {
      nome: '', email: '', telefone: '', dataNascimento: '',
      endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
      status: 'ATIVO', estadoCivil: '',
      ministerio: '', observacoes: '',
    },
    requiredFields: ['nome'],
  })

  const { reset } = form

  useEffect(() => {
    if (membroInicial) {
      reset({
        nome: membroInicial.nome,
        email: membroInicial.email ?? '',
        telefone: membroInicial.telefone ? formatarTelefone(membroInicial.telefone) : '',
        dataNascimento: membroInicial.dataNascimento ?? '',
        endereco: {
          cep: membroInicial.endereco?.cep ? formatarCep(membroInicial.endereco.cep) : '',
          logradouro: membroInicial.endereco?.logradouro ?? '',
          numero: membroInicial.endereco?.numero ?? '',
          complemento: membroInicial.endereco?.complemento ?? '',
          bairro: membroInicial.endereco?.bairro ?? '',
          cidade: membroInicial.endereco?.cidade ?? '',
          uf: membroInicial.endereco?.uf ?? '',
        },
        status: membroInicial.status,
        estadoCivil: membroInicial.estadoCivil ?? '',
        ministerio: membroInicial.ministerio ?? '',
        observacoes: membroInicial.observacoes ?? '',
      })
    }
    
  }, [membroInicial, reset])

  const onSubmit = async (data: MembroFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: MembroRequest = {
        ...data,
        telefone: data.telefone?.replace(/\D/g, '') || undefined,
        estadoCivil: data.estadoCivil || undefined,
        endereco: {
          ...data.endereco,
          cep: data.endereco?.cep?.replace(/\D/g, '') || undefined,
        },
    }

      if (ehEdicao) {
        await membrosService.atualizar(membroId!, payload)
        invalidarCache(queryClient, 'membro')
        queryClient.invalidateQueries({ queryKey: ['membro', membroId] })
        notificar.sucesso('Membro atualizado com sucesso!')
      } else {
        await membrosService.criar(payload)
        invalidarCache(queryClient, 'membro')
        notificar.sucesso('Membro cadastrado com sucesso!')
      }
      router.push('/membros')
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'EMAIL_DUPLICADO') {
          form.setError('email', { type: 'server', message: e.message })
          return
        }
        setErroGeral(e?.message ?? 'Erro ao salvar membro. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar membro. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { ...form, onSubmit, erroGeral, isLoading, ehEdicao }
}