import { useRouter } from 'next/navigation'
import { useState, useEffect, useRef, useCallback } from 'react'
import axios from 'axios'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useQueryClient } from '@tanstack/react-query'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { zodResolver } from '@hookform/resolvers/zod'
import { useAppForm } from '../forms/useAppForm'
import { eventoSchema, type EventoFormInput, type EventoFormData } from '@/lib/validators'
import { eventosService } from '@/services/evento.service'
import type { EventoRequest, EventoResponse, InscritoImpactado, RecorrenciaRequest, DiaSemana, EscopoEdicaoEvento } from '@/types/evento.type'
import type { ApiError } from '@/types/api.types'

function montarRecorrencia(data: EventoFormData): RecorrenciaRequest {
  return {
    frequencia: data.recorrenciaFrequencia!,
    intervalo: data.recorrenciaIntervalo ?? 1,
    diasSemana: data.recorrenciaFrequencia === 'SEMANAL'
      ? (data.recorrenciaDiasSemana as DiaSemana[])
      : undefined,
    tipoRecorrenciaMensal: data.recorrenciaFrequencia === 'MENSAL'
      ? data.recorrenciaTipoMensal
      : undefined,
    dataFim: data.recorrenciaFimTipo === 'DATA' ? data.recorrenciaDataFim : undefined,
    numeroOcorrencias: data.recorrenciaFimTipo === 'CONTAGEM' ? data.recorrenciaNumeroOcorrencias : undefined,
  }
}

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

  const [impactoAfetados, setImpactoAfetados] = useState<InscritoImpactado[] | null>(null)
  const [isVerificandoImpacto, setIsVerificandoImpacto] = useState(false)
  const [payloadPendente, setPayloadPendente] = useState<EventoRequest | null>(null)

  // Campos personalizados agora podem ser montados ANTES do evento existir (evento novo,
  // sem id ainda) — o painel registra aqui como salvar a si mesmo, e o próprio salvarEvento
  // chama isso logo depois de criar/atualizar o evento, já com o id definitivo em mãos.
  // Só existe pra permitir que CamposPersonalizadosPainel viva fora do conhecimento de
  // criar-vs-editar; sem isso, EventoForm precisaria orquestrar duas chamadas assíncronas
  // em ordens diferentes conforme o modo.
  const salvarCamposPersonalizadosRef = useRef<((eventoId: string) => Promise<void>) | null>(null)
  const registrarSalvarCamposPersonalizados = useCallback(
    (fn: ((eventoId: string) => Promise<void>) | null) => {
      salvarCamposPersonalizadosRef.current = fn
    },
    [],
  )

  // Evento que pertence a uma série pergunta o alcance (só este/estes e os seguintes/toda a
  // série) antes de qualquer outra coisa — payload fica pendente até a escolha chegar.
  const [payloadAguardandoEscopo, setPayloadAguardandoEscopo] = useState<EventoRequest | null>(null)
  const [escopoEscolhido, setEscopoEscolhido] = useState<EscopoEdicaoEvento>('ESTA')

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
      restritoPropriaIgreja: false,
      repetir: false,
      recorrenciaFrequencia: undefined,
      recorrenciaIntervalo: undefined,
      recorrenciaDiasSemana: [],
      recorrenciaTipoMensal: undefined,
      recorrenciaFimTipo: 'NUNCA',
      recorrenciaDataFim: undefined,
      recorrenciaNumeroOcorrencias: undefined,
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
        restritoPropriaIgreja: eventoInicial.restritoPropriaIgreja ?? false,
        // Recorrência não se edita por aqui (usa o seletor de escopo), mas os campos
        // precisam estar presentes no reset() mesmo assim — reset() substitui TODOS os
        // valores do form pelos fornecidos; uma chave ausente aqui vira uma chave AUSENTE
        // (não apenas undefined) no objeto que o zodResolver recebe, e um bug real do Zod
        // 4 faz `z.preprocess(...).optional()` rejeitar chave ausente com "expected
        // nonoptional" mesmo aceitando `undefined` explícito — por isso todo campo tem que
        // vir default aqui, nunca ficar de fora do objeto.
        repetir: false,
        recorrenciaFrequencia: undefined,
        recorrenciaIntervalo: undefined,
        recorrenciaDiasSemana: [],
        recorrenciaTipoMensal: undefined,
        recorrenciaFimTipo: 'NUNCA',
        recorrenciaDataFim: undefined,
        recorrenciaNumeroOcorrencias: undefined,
      })
    }
  }, [eventoInicial, reset])

  // Grava de fato (POST no cadastro, PUT na edição). Isolado do onSubmit porque o PUT
  // pode acontecer em dois momentos: direto (sem impacto) ou só depois da escolha do
  // admin no <ModalImpactoRestricao> — mas o "salvar" em si é sempre o mesmo código.
  async function salvarEvento(payload: EventoRequest, cancelarNaoElegiveis: boolean, escopo?: EscopoEdicaoEvento) {
    setIsLoading(true)
    try {
      if (ehEdicao) {
        await eventosService.atualizar(eventoId!, payload, cancelarNaoElegiveis, escopo)
        await salvarCamposPersonalizadosRef.current?.(eventoId!)
        invalidarCache(queryClient, 'evento')
        queryClient.invalidateQueries({ queryKey: ['evento', eventoId] })
        notificar.sucesso('Evento atualizado com sucesso!')
      } else {
        const criado = await eventosService.criar(payload)
        await salvarCamposPersonalizadosRef.current?.(criado.id)
        invalidarCache(queryClient, 'evento')
        notificar.sucesso('Evento cadastrado com sucesso!')
      }
      router.back()
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
      const inicioEm = `${data.inicioData}T${data.inicioHora}:00`
      const fimEm = (data.fimData && data.fimHora)
        ? `${data.fimData}T${data.fimHora}:00`
        : undefined

      // PUT substitui a entidade inteira; booleano ausente no JSON vira false no backend.
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
        // Forçado a false quando requerInscricao=false, mesmo que o form tenha valor de edição anterior.
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
        restritoPropriaIgreja: data.restritoPropriaIgreja,
        // Recorrência só se aplica ao cadastro — editar uma ocorrência existente usa o
        // seletor de escopo (só esta/esta e as seguintes/toda a série), não este toggle.
        recorrencia: (!ehEdicao && data.repetir) ? montarRecorrencia(data) : null,
      }

      // Evento novo nunca tem inscritos — vai direto, sem checar impacto.
      if (!ehEdicao) {
        await salvarEvento(payload, false)
        return
      }

      // Evento que pertence a uma série pergunta o alcance antes de qualquer outra coisa —
      // a checagem de impacto (abaixo) só roda depois que o admin escolher o escopo.
      if (eventoInicial?.serieId) {
        setPayloadAguardandoEscopo(payload)
        return
      }

      await verificarImpactoEProsseguir(payload)
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

  // Pergunta ao backend quem ficaria de fora com esta versão do evento; só abre o modal
  // de impacto se de fato sobrar alguém afetado. Roda depois do escopo (se aplicável),
  // porque só faz sentido conferir impacto no payload que realmente vai ser gravado.
  async function verificarImpactoEProsseguir(payload: EventoRequest) {
    setIsVerificandoImpacto(true)
    try {
      const { afetados } = await eventosService.impactoRestricao(eventoId!, payload)
      if (afetados.length === 0) {
        await salvarEvento(payload, false, escopoEscolhido)
      } else {
        setPayloadPendente(payload)
        setImpactoAfetados(afetados)
      }
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar evento. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar evento. Tente novamente.')
      }
    } finally {
      setIsVerificandoImpacto(false)
    }
  }

  // Escolha do admin no <ModalEscopoEdicaoEvento> (evento de série): guarda o escopo e
  // segue pro mesmo fluxo de verificação de impacto que evento avulso já usa.
  async function onEscolherEscopoEdicao(escopo: EscopoEdicaoEvento) {
    if (!payloadAguardandoEscopo) return
    setEscopoEscolhido(escopo)
    const payload = payloadAguardandoEscopo
    setPayloadAguardandoEscopo(null)
    setIsVerificandoImpacto(true)
    try {
      const { afetados } = await eventosService.impactoRestricao(eventoId!, payload)
      if (afetados.length === 0) {
        await salvarEvento(payload, false, escopo)
      } else {
        setPayloadPendente(payload)
        setImpactoAfetados(afetados)
      }
    } catch (error: unknown) {
      if (axios.isAxiosError<ApiError>(error)) {
        setErroGeral(error.response?.data?.message ?? 'Erro ao salvar evento. Tente novamente.')
      } else {
        setErroGeral('Erro ao salvar evento. Tente novamente.')
      }
    } finally {
      setIsVerificandoImpacto(false)
    }
  }

  function onFecharEscopoEdicao() {
    setPayloadAguardandoEscopo(null)
  }

  // Escolha do admin no <ModalImpactoRestricao>: cancelarNaoElegiveis vira o parâmetro
  // do PUT. O payload já foi validado pelo impacto — não recalcula nada, só decide.
  async function onConfirmarImpacto(cancelarNaoElegiveis: boolean) {
    if (!payloadPendente) return
    await salvarEvento(payloadPendente, cancelarNaoElegiveis, escopoEscolhido)
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
    registrarSalvarCamposPersonalizados,
    impactoAfetados, isVerificandoImpacto, onConfirmarImpacto, onFecharImpacto,
    aguardandoEscopoEdicao: !!payloadAguardandoEscopo,
    onEscolherEscopoEdicao, onFecharEscopoEdicao,
  }
}