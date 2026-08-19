import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '@/hooks/forms/useAppForm'
import { z } from 'zod'
import { visitanteService } from '@/services/visitante.service'
import { formatarTelefone, formatarCep } from '@/lib/masks'
import type { VisitanteRequest, VisitanteResponse } from '@/types/visitante.type'
import type { ApiError } from '@/types/api.types'
import type { DefaultValues } from 'react-hook-form'

const visitanteSchema = z.object({
  nome: z.string().min(1, 'Nome é obrigatório.').max(255),
  telefone: z.string().optional(),
  dataNascimento: z.string().optional(),
  sexo: z.string().optional(),
  estadoCivil: z.string().optional(),
  endereco: z.object({
    cep: z.string().optional(),
    logradouro: z.string().optional(),
    numero: z.string().optional(),
    complemento: z.string().optional(),
    bairro: z.string().optional(),
    cidade: z.string().optional(),
    uf: z.string().max(2, 'UF deve ter 2 letras').optional(),
  }),
  temFilhos: z.boolean().optional(),
  quantidadeFilhos: z.number().nullable().optional(),
  observacoes: z.string().optional(),
})

type VisitanteFormData = z.infer<typeof visitanteSchema>
type VisitanteFormInput = z.input<typeof visitanteSchema>

const defaultValues: DefaultValues<VisitanteFormInput> = {
  nome: '', telefone: '', dataNascimento: '', sexo: '', estadoCivil: '',
  endereco: { cep: '', logradouro: '', numero: '', complemento: '', bairro: '', cidade: '', uf: '' },
  temFilhos: false, quantidadeFilhos: null, observacoes: '',
}

interface UseVisitanteFormParams {
  visitanteId?: string
  visitanteInicial?: VisitanteResponse
}

export function useVisitanteForm({ visitanteId, visitanteInicial }: UseVisitanteFormParams = {}) {
  const router = useRouter()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!visitanteId

  const form = useAppForm<VisitanteFormInput, VisitanteFormData>({
    resolver: zodResolver(visitanteSchema),
    defaultValues,
    requiredFields: ['nome'],
  })

  const { reset } = form

  useEffect(() => {
    if (visitanteInicial) {
      reset({
        nome: visitanteInicial.nome,
        telefone: visitanteInicial.telefone ? formatarTelefone(visitanteInicial.telefone) : '',
        dataNascimento: visitanteInicial.dataNascimento ?? '',
        sexo: visitanteInicial.sexo ?? '',
        estadoCivil: visitanteInicial.estadoCivil ?? '',
        endereco: {
          cep: visitanteInicial.endereco?.cep ? formatarCep(visitanteInicial.endereco.cep) : '',
          logradouro: visitanteInicial.endereco?.logradouro ?? '',
          numero: visitanteInicial.endereco?.numero ?? '',
          complemento: visitanteInicial.endereco?.complemento ?? '',
          bairro: visitanteInicial.endereco?.bairro ?? '',
          cidade: visitanteInicial.endereco?.cidade ?? '',
          uf: visitanteInicial.endereco?.uf ?? '',
        },
        temFilhos: visitanteInicial.temFilhos,
        quantidadeFilhos: visitanteInicial.quantidadeFilhos,
        observacoes: visitanteInicial.observacoes ?? '',
      })
    }
  }, [visitanteInicial, reset])

  const onSubmit = async (data: VisitanteFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: VisitanteRequest = {
        ...data,
        telefone: data.telefone?.replace(/\D/g, '') || undefined,
        sexo: (data.sexo as 'HOMEM' | 'MULHER' | undefined) || undefined,
        estadoCivil: (data.estadoCivil as 'SOLTEIRO' | 'CASADO' | 'DIVORCIADO' | 'VIUVO' | undefined) || undefined,
        dataNascimento: data.dataNascimento || undefined,
        endereco: data.endereco ? {
          ...data.endereco,
          cep: data.endereco.cep?.replace(/\D/g, '') || undefined,
        } : undefined,
        temFilhos: data.temFilhos ?? false,
        quantidadeFilhos: data.temFilhos ? (data.quantidadeFilhos ?? undefined) : undefined,
        observacoes: data.observacoes || undefined,
      }

      if (ehEdicao) {
        await visitanteService.atualizar(visitanteId!, payload)
        queryClient.invalidateQueries({ queryKey: ['visitante', visitanteId] })
        invalidarCache(queryClient, 'visitante')
        notificar.sucesso('Visitante atualizado com sucesso!')
      } else {
        await visitanteService.criar(payload)
        invalidarCache(queryClient, 'visitante')
        notificar.sucesso('Visitante cadastrado com sucesso!')
      }

      router.back()
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { ...form, onSubmit, erroGeral, isLoading, ehEdicao }
}
