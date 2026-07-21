import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../forms/useAppForm'
import { eventoSchema, type EventoFormInput, type EventoFormData } from '@/lib/validators'
import { dataBRParaISO, isoParaDataBR } from '@/lib/masks'
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
      requerInscricao: false,
      vagas: undefined,
      tipoInscricao: 'GRATUITO',
      preco: undefined,
      exclusivoMembros: false,
      exclusivoBatizados: false,
    },
    requiredFields: ['titulo', 'inicioData', 'inicioHora'],
  })

  const { reset } = form

  useEffect(() => {
    if (eventoInicial) {
      const [inicioDataIso, inicioHoraFull] = eventoInicial.inicioEm.split('T')
      const inicioData = isoParaDataBR(inicioDataIso)
      const inicioHora = inicioHoraFull ? inicioHoraFull.slice(0, 5) : ''

      let fimData = ''
      let fimHora = ''
      if (eventoInicial.fimEm) {
        const [fDataIso, fHoraFull] = eventoInicial.fimEm.split('T')
        fimData = isoParaDataBR(fDataIso)
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
        requerInscricao: eventoInicial.requerInscricao,
        vagas: eventoInicial.vagas ?? undefined,
        tipoInscricao: eventoInicial.preco != null ? 'PAGO' : 'GRATUITO',
        preco: eventoInicial.preco ?? undefined,
        exclusivoMembros: eventoInicial.exclusivoMembros,
        exclusivoBatizados: eventoInicial.exclusivoBatizados,
      })
    }
  }, [eventoInicial, reset])

  const onSubmit = async (data: EventoFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      // Os campos viajam no form em dd/mm/aaaa (ver lib/masks); o backend espera ISO.
      const inicioDataIso = dataBRParaISO(data.inicioData)
      const fimDataIso = data.fimData ? dataBRParaISO(data.fimData) : undefined
      const inicioEm = `${inicioDataIso}T${data.inicioHora}:00`
      const fimEm = (fimDataIso && data.fimHora)
        ? `${fimDataIso}T${data.fimHora}:00`
        : undefined

      // O backend faz PUT (substitui a entidade inteira) e lê booleano JSON ausente como
      // false. Por isso requerInscricao/exclusivoMembros/exclusivoBatizados são SEMPRE
      // enviados com o valor atual do form — mesmo quando a seção "Inscrições" está
      // recolhida na tela — nunca omitidos condicionalmente. O RHF não desmonta esses
      // campos do estado do form ao escondê-los (shouldUnregister não está ativo em
      // useAppForm), então o valor sobrevive intacto até aqui mesmo com o input invisível.
      const payload: EventoRequest = {
        titulo: data.titulo,
        descricao: data.descricao || undefined,
        inicioEm,
        fimEm,
        local: data.local || undefined,
        requerInscricao: data.requerInscricao,
        exclusivoMembros: data.exclusivoMembros,
        exclusivoBatizados: data.exclusivoBatizados,
        vagas: data.requerInscricao ? data.vagas : undefined,
        preco: (data.requerInscricao && data.tipoInscricao === 'PAGO' && data.preco != null)
          ? data.preco
          : undefined,
      }

      if (ehEdicao) {
        await eventosService.atualizar(eventoId!, payload)
        invalidarCache(queryClient, 'evento')
        queryClient.invalidateQueries({ queryKey: ['evento', eventoId] })
        notificar.sucesso('Evento atualizado com sucesso!')
      } else {
        await eventosService.criar(payload)
        invalidarCache(queryClient, 'evento')
        notificar.sucesso('Evento cadastrado com sucesso!')
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