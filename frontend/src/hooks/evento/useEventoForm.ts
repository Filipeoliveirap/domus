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
import type { EventoRequest, EventoResponse, InscritoImpactado } from '@/types/evento.type'
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

  // ─── Impacto retroativo (Task 9) ───
  // Ao editar, o payload calculado no clique de "salvar" fica aqui esperando a escolha
  // do admin no <ModalImpactoRestricao> — sem isso teríamos que recalcular tudo de novo.
  const [impactoAfetados, setImpactoAfetados] = useState<InscritoImpactado[] | null>(null)
  const [isVerificandoImpacto, setIsVerificandoImpacto] = useState(false)
  const [payloadPendente, setPayloadPendente] = useState<EventoRequest | null>(null)

  const form = useAppForm<EventoFormInput, EventoFormData>({
    resolver: zodResolver(eventoSchema),
    defaultValues: {
      titulo: '', descricao: '',
      inicioData: '', inicioHora: '',
      fimData: '', fimHora: '',
      localId: undefined, localTexto: undefined,
      tipo: '', responsavelPessoaId: undefined,
      requerInscricao: false,
      controlaPresenca: false,
      vagas: undefined,
      tipoInscricao: 'GRATUITO',
      preco: undefined,
      exclusivoMembros: false,
      recorteEtario: null,
      idadeMin: undefined,
      idadeMax: undefined,
      restricaoEstadoCivil: null,
      restricaoSexo: null,
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
        // O backend devolve `local` já resolvido (objeto): se tem `id`, é um LocalEvento
        // cadastrado e reidrata o select; se `id` é null, era texto ad-hoc e volta pro campo
        // livre. Nunca os dois — espelha a mesma exclusividade do envio.
        localId: eventoInicial.local?.id ?? undefined,
        localTexto: eventoInicial.local && eventoInicial.local.id == null
          ? eventoInicial.local.nome
          : undefined,
        tipo: eventoInicial.tipo ?? '',
        responsavelPessoaId: eventoInicial.responsavel?.id ?? undefined,
        requerInscricao: eventoInicial.requerInscricao,
        controlaPresenca: eventoInicial.controlaPresenca,
        vagas: eventoInicial.vagas ?? undefined,
        tipoInscricao: eventoInicial.preco != null ? 'PAGO' : 'GRATUITO',
        // String() na BORDA, como o formulário de movimentação já faz com `valor`: a API
        // manda número e a máscara de dinheiro trabalha com string. Sem isto, editar um
        // evento pago falhava na validação até a pessoa apagar e redigitar o valor.
        preco: eventoInicial.preco != null ? String(eventoInicial.preco) : undefined,
        exclusivoMembros: eventoInicial.exclusivoMembros,
        // Reidratação da elegibilidade: o <BlocoParaQuemE> deriva "modo faixa" destes
        // valores (idadeMin/idadeMax/restrições), não de um campo próprio — chegando
        // preenchidos, o rádio já abre em "Faixa específica" sozinho.
        recorteEtario: eventoInicial.recorteEtario,
        idadeMin: eventoInicial.idadeMin ?? undefined,
        idadeMax: eventoInicial.idadeMax ?? undefined,
        restricaoEstadoCivil: eventoInicial.restricaoEstadoCivil,
        restricaoSexo: eventoInicial.restricaoSexo,
        fotoId: eventoInicial.fotoId ?? null,
      })
    }
  }, [eventoInicial, reset])

  // Grava de fato (POST no cadastro, PUT na edição). Isolado do onSubmit porque o PUT
  // pode acontecer em dois momentos: direto (sem impacto) ou só depois da escolha do
  // admin no <ModalImpactoRestricao> — mas o "salvar" em si é sempre o mesmo código.
  async function salvarEvento(payload: EventoRequest, cancelarNaoElegiveis: boolean) {
    setIsLoading(true)
    try {
      if (ehEdicao) {
        await eventosService.atualizar(eventoId!, payload, cancelarNaoElegiveis)
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

  const onSubmit = async (data: EventoFormData) => {
    setErroGeral(null)
    try {
      // Já em ISO: o CampoData guarda aaaa-mm-dd no form e só exibe em pt-BR.
      const inicioEm = `${data.inicioData}T${data.inicioHora}:00`
      const fimEm = (data.fimData && data.fimHora)
        ? `${data.fimData}T${data.fimHora}:00`
        : undefined

      // O backend faz PUT (substitui a entidade inteira) e lê booleano JSON ausente como
      // false. Por isso requerInscricao/exclusivoMembros/elegibilidade são SEMPRE
      // enviados com o valor atual do form — mesmo quando a seção "Inscrições" está
      // recolhida na tela — nunca omitidos condicionalmente. O RHF não desmonta esses
      // campos do estado do form ao escondê-los (shouldUnregister não está ativo em
      // useAppForm), então o valor sobrevive intacto até aqui mesmo com o input invisível.
      const payload: EventoRequest = {
        titulo: data.titulo,
        descricao: data.descricao || undefined,
        inicioEm,
        fimEm,
        // localId e localTexto são exclusivos — o <SeletorLocal> já garante que só um venha
        // preenchido, mas normalizo aqui também: string vazia vira undefined pra não enviar
        // "" e cair no CHECK do banco por engano.
        localId: data.localId || undefined,
        localTexto: data.localTexto || undefined,
        tipo: data.tipo || undefined,
        responsavelPessoaId: data.responsavelPessoaId || null,
        requerInscricao: data.requerInscricao,
        // Mesmo raciocínio de vagas/preço: sempre enviado com o valor atual do form (nunca
        // omitido condicionalmente), mesmo quando a seção está escondida — o PUT substitui a
        // entidade inteira e um campo ausente vira false. Forçado a false quando
        // requerInscricao=false: o toggle já fica desabilitado nesse estado, mas o
        // valor no form pode ter sobrevivido de uma edição anterior.
        controlaPresenca: data.requerInscricao ? data.controlaPresenca : false,
        exclusivoMembros: data.exclusivoMembros,
        vagas: data.requerInscricao ? data.vagas : undefined,
        preco: (data.requerInscricao && data.tipoInscricao === 'PAGO' && data.preco != null)
          ? data.preco
          : undefined,
        recorteEtario: data.recorteEtario ?? null,
        idadeMin: data.idadeMin ?? null,
        idadeMax: data.idadeMax ?? null,
        restricaoEstadoCivil: data.restricaoEstadoCivil ?? null,
        restricaoSexo: data.restricaoSexo ?? null,
        fotoId: data.fotoId ?? null,
      }

      // Evento novo nunca tem inscritos — vai direto, sem checar impacto. Na edição,
      // primeiro pergunta ao backend quem ficaria de fora com esta versão do evento;
      // só abre o modal se de fato sobrar alguém afetado.
      if (!ehEdicao) {
        await salvarEvento(payload, false)
        return
      }

      setIsVerificandoImpacto(true)
      try {
        const { afetados } = await eventosService.impactoRestricao(eventoId!, payload)
        if (afetados.length === 0) {
          await salvarEvento(payload, false)
        } else {
          setPayloadPendente(payload)
          setImpactoAfetados(afetados)
        }
      } finally {
        setIsVerificandoImpacto(false)
      }
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
    }
  }

  // Escolha do admin no <ModalImpactoRestricao>: cancelarNaoElegiveis vira o parâmetro
  // do PUT. O payload já foi validado pelo impacto — não recalcula nada, só decide.
  async function onConfirmarImpacto(cancelarNaoElegiveis: boolean) {
    if (!payloadPendente) return
    await salvarEvento(payloadPendente, cancelarNaoElegiveis)
    setImpactoAfetados(null)
    setPayloadPendente(null)
  }

  function onFecharImpacto() {
    setImpactoAfetados(null)
    setPayloadPendente(null)
  }

  // O <SeletorResponsavel> só recebe o id; o nome inicial (edição) vem daqui para ele
  // poder exibir quem já é o responsável sem uma busca extra.
  const responsavelNomeInicial = eventoInicial?.responsavel?.nome
  return {
    ...form, onSubmit, erroGeral, isLoading, ehEdicao, responsavelNomeInicial,
    impactoAfetados, isVerificandoImpacto, onConfirmarImpacto, onFecharImpacto,
  }
}