import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { toast } from 'sonner'
import { useQueryClient } from '@tanstack/react-query'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../forms/useAppForm'
import { eventoSchema, type EventoFormInput, type EventoFormData } from '@/lib/validators'
import { eventosService } from '@/services/evento.service'
import type { EventoRequest, EventoResponse } from '@/types/evento.type'
import type { ApiError } from '@/types/api.types'

interface UseEventoFormParams {
  eventoId?: string
  eventoInicial?: EventoResponse
}

export function useEventoForm({ eventoId, eventoInicial }: UseEventoFormParams = {}) {
  const router = useRouter()
  const [erroGeral, setErroGeral] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)
  const queryClient = useQueryClient()
  const ehEdicao = !!eventoId

  const form = useAppForm<EventoFormInput, EventoFormData>({
    resolver: zodResolver(eventoSchema),
    defaultValues: {
      titulo: '', descricao: '',
      inicioData: '', inicioHora: '',
      fimData: '', fimHora: '',
      local: '',
    },
    requiredFields: ['titulo', 'inicioData', 'inicioHora'],
  })

  const { reset } = form

  useEffect(() => {
    if (eventoInicial) {
      const [inicioData, inicioHoraFull] = eventoInicial.inicioEm.split('T')
      const inicioHora = inicioHoraFull ? inicioHoraFull.slice(0, 5) : ''  

      let fimData = ''
      let fimHora = ''
      if (eventoInicial.fimEm) {
        const [fData, fHoraFull] = eventoInicial.fimEm.split('T')
        fimData = fData
        fimHora = fHoraFull ? fHoraFull.slice(0, 5) : ''
      }

      reset({
        titulo: eventoInicial.titulo,
        descricao: eventoInicial.descricao ?? '',
        inicioData,
        inicioHora,
        fimData,
        fimHora,
        local: eventoInicial.local ?? '',
      })
    }
  }, [eventoInicial, reset])

  const onSubmit = async (data: EventoFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      const inicioEm = `${data.inicioData}T${data.inicioHora}:00`
      const fimEm = (data.fimData && data.fimHora)
        ? `${data.fimData}T${data.fimHora}:00`
        : undefined

      const payload: EventoRequest = {
        titulo: data.titulo,
        descricao: data.descricao || undefined,
        inicioEm,
        fimEm,
        local: data.local || undefined,
      }

      if (ehEdicao) {
        await eventosService.atualizar(eventoId!, payload)
        queryClient.invalidateQueries({ queryKey: ['eventos'] })
        queryClient.invalidateQueries({ queryKey: ['evento', eventoId] })
        toast.success('Evento atualizado com sucesso!')
      } else {
        await eventosService.criar(payload)
        queryClient.invalidateQueries({ queryKey: ['eventos'] })
        toast.success('Evento cadastrado com sucesso!')
      }
      router.push('/eventos')
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        const e = error.response?.data
        if (e?.error === 'DATA_INVALIDA') {
          form.setError('fimData', { type: 'server', message: e.message })
          return
        }
        setErroGeral(e?.message ?? 'Erro ao salvar evento. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar evento. Tente novamente.')
      }
    } finally {
      setIsLoading(false)
    }
  }

  return { ...form, onSubmit, erroGeral, isLoading, ehEdicao }
}