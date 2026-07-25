import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '@/hooks/forms/useAppForm'
import { z } from 'zod'
import { celulaService } from '@/services/celula.service'
import { formatarHoraDigitada } from '@/lib/masks'
import type { CelulaRequest, CelulaResponse } from '@/types/celula.type'
import type { ApiError } from '@/types/api.types'
import type { DefaultValues } from 'react-hook-form'

const celulaSchema = z.object({
  nome: z.string().min(1, 'Nome é obrigatório.').max(150),
  diaSemana: z.string().optional(),
  horario: z.string().regex(/^([01]\d|2[0-3]):[0-5]\d$/, 'Horário inválido. Use hh:mm.').optional().or(z.literal('')),
})

type CelulaFormData = z.infer<typeof celulaSchema>
type CelulaFormInput = z.input<typeof celulaSchema>

const defaultValues: DefaultValues<CelulaFormInput> = {
  nome: '', diaSemana: '', horario: '',
}

interface UseCelulaFormParams {
  celulaId?: string
  celulaInicial?: CelulaResponse
}

export function useCelulaForm({ celulaId, celulaInicial }: UseCelulaFormParams = {}) {
  const router = useRouter()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!celulaId

  const form = useAppForm<CelulaFormInput, CelulaFormData>({
    resolver: zodResolver(celulaSchema),
    defaultValues,
    requiredFields: ['nome'],
  })

  const { reset } = form

  useEffect(() => {
    if (celulaInicial) {
      reset({
        nome: celulaInicial.nome,
        diaSemana: celulaInicial.diaSemana ?? '',
        horario: celulaInicial.horario ? celulaInicial.horario.slice(0, 5) : '',
      })
    }
  }, [celulaInicial, reset])

  const onSubmit = async (data: CelulaFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const payload: CelulaRequest = {
        nome: data.nome,
        diaSemana: (data.diaSemana || undefined) as CelulaRequest['diaSemana'],
        horario: data.horario ? data.horario + ':00' : undefined,
      }

      if (ehEdicao) {
        await celulaService.atualizar(celulaId!, payload)
        invalidarCache(queryClient, 'celula')
        notificar.sucesso('Célula atualizada com sucesso!')
      } else {
        await celulaService.criar(payload)
        invalidarCache(queryClient, 'celula')
        notificar.sucesso('Célula criada com sucesso!')
      }
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
