import { useRouter } from 'next/navigation'
import { useState, useEffect } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
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
      requerInscricao: false,
      vagas: undefined,
      tipoInscricao: 'GRATUITO',
      preco: undefined,
      exclusivoMembros: false,
      fotoId: null,
    },
    requiredFields: ['titulo', 'inicioData', 'inicioHora'],
  })

  const { reset } = form

  useEffect(() => {
    if (eventoInicial) {
      // O CampoData fala ISO, igual ao <input type="date"> que ele substitui — então o
      // valor do form JÁ é o formato que o backend espera, sem conversão de ida e volta.
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
        requerInscricao: eventoInicial.requerInscricao,
        vagas: eventoInicial.vagas ?? undefined,
        tipoInscricao: eventoInicial.preco != null ? 'PAGO' : 'GRATUITO',
        // String() na BORDA, como o formulário de movimentação já faz com `valor`: a API
        // manda número e a máscara de dinheiro trabalha com string. Sem isto, editar um
        // evento pago falhava na validação até a pessoa apagar e redigitar o valor.
        preco: eventoInicial.preco != null ? String(eventoInicial.preco) : undefined,
        exclusivoMembros: eventoInicial.exclusivoMembros,
        fotoId: eventoInicial.fotoId ?? null,
      })
    }
  }, [eventoInicial, reset])

  const onSubmit = async (data: EventoFormData) => {
    setErroGeral(null)
    setIsLoading(true)
    try {
      // Já em ISO: o CampoData guarda aaaa-mm-dd no form e só exibe em pt-BR.
      const inicioEm = `${data.inicioData}T${data.inicioHora}:00`
      const fimEm = (data.fimData && data.fimHora)
        ? `${data.fimData}T${data.fimHora}:00`
        : undefined

      // O backend faz PUT (substitui a entidade inteira) e lê booleano JSON ausente como
      // false. Por isso requerInscricao/exclusivoMembros são SEMPRE
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
        vagas: data.requerInscricao ? data.vagas : undefined,
        preco: (data.requerInscricao && data.tipoInscricao === 'PAGO' && data.preco != null)
          ? data.preco
          : undefined,
        fotoId: data.fotoId ?? null,
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