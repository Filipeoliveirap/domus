'use client'

import { useRef, useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CheckCircle2, XCircle, ThumbsUp, AlertTriangle, Clock } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useElegibilidade } from '@/hooks/inscricao/useElegibilidade'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { useMinhaPessoa } from '@/hooks/pessoa/useMinhaPessoa'
import { useDefinirEmailInicial } from '@/hooks/pessoa/useDefinirEmailInicial'
import { useCamposPersonalizados } from '@/hooks/evento/useCamposPersonalizados'
import { useResponderCampos } from '@/hooks/inscricao/useResponderCampos'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import { ModalCompletarDadosInscricao } from './ModalCompletarDadosInscricao'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { notificar } from '@/components/common/Notificacao/notificar'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import type { SituacaoEvento } from '@/types/evento.type'
import type { Impedimento, MinhaInscricaoResponse } from '@/types/inscricao.type'
import styles from './BotaoConfirmarPresenca.module.css'

interface Props {
  eventoId: string
  inicioEm: string
  vagasRestantes: number | null
  requerInscricao: boolean
  situacao: SituacaoEvento
  preco?: number | null
  /** Chamado só quando a inscrição exige confirmação prévia (requerInscricao) e deu certo,
   *  SEM pagamento pendente — evento pago com sucesso navega pra rota de checkout em vez
   *  de chamar isto (o drawer não teria o que abrir; a pessoa já saiu da tela). */
  onInscritoComSucesso?: () => void
}

export function BotaoConfirmarPresenca({
  eventoId, inicioEm, vagasRestantes, requerInscricao, situacao, preco, onInscritoComSucesso,
}: Props) {
  const router = useRouter()
  const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false)
  const [semConta, setSemConta] = useState(false)
  // A mutation já resolveu (isPending vira false) antes do router.push completar a
  // navegação — sem isto, o botão "pisca" de volta pro texto normal por um instante
  // enquanto a rota de checkout ainda está carregando.
  const [navegandoParaCheckout, setNavegandoParaCheckout] = useState(false)
  // 422 contornável: gestor quebrando recorte de elegibilidade
  const [impedimentosParaConfirmar, setImpedimentosParaConfirmar] = useState<Impedimento[] | null>(null)
  // E-mail obrigatório (2026-08-27) + campos personalizados: se faltar algo, abre este
  // modal ANTES de inscrever — a ação real (toggle "Eu vou" ou "Se inscrever" com/sem
  // preço) fica guardada em `aposCompletarDadosRef`, chamada só depois de confirmar.
  const [completandoDados, setCompletandoDados] = useState(false)
  const aposCompletarDadosRef = useRef<() => void>(() => {})

  const role = useAuthStore((s) => s.role)
  // Gestor ignora restrições com confirmação extra
  const ehGestor = podeGerenciarInscricoes(role)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  // Status da conta MP da própria igreja — só importa quando o evento é pago.
  const { data: contaPagamento } = useContaPagamento()
  const { data: minhaPessoa } = useMinhaPessoa()
  const { data: camposPersonalizados } = useCamposPersonalizados(eventoId)
  const campos = camposPersonalizados ?? []
  const precisaCompletarDados = !minhaPessoa?.email || campos.length > 0
  const definirEmail = useDefinirEmailInicial()
  const { responder } = useResponderCampos()

  // Modo "Eu vou": sem toast, feedback é o próprio botão. Evento pago também silencia o
  // toast genérico ("Inscrição confirmada!" seria enganoso — o pagamento ainda não foi
  // feito; a rota de checkout mostra o próprio feedback quando o pagamento é aprovado).
  const inscrever = useInscrever(eventoId, !requerInscricao || !!preco, {
    onContornavel: ehGestor ? (imps) => setImpedimentosParaConfirmar(imps) : undefined,
  })
  const cancelar = useCancelarInscricao(!requerInscricao)

  // Gestor vê o motivo, mas o botão segue ativo (422 abre confirmação)
  const { data: elegibilidade } = useElegibilidade(eventoId)
  const impedimentoPreview = !elegibilidade?.apto ? elegibilidade?.impedimentos[0]?.mensagem : undefined
  const impedimento = ehGestor ? undefined : impedimentoPreview

  const eventoEncerrado = new Date(inicioEm) < new Date()
  const semVagas = vagasRestantes !== null && vagasRestantes <= 0
  const inscricaoBloqueadaPelaSituacao = situacao !== 'AGENDADO'

  // Ponto único que decide se pode inscrever direto ou precisa abrir o modal de dados
  // antes. `acao` é a inscrição de verdade (o que cada botão já fazia), guardada pra
  // rodar depois que o modal confirmar (ou direto, se não faltar nada).
  function tentarInscrever(acao: () => void) {
    if (precisaCompletarDados) {
      aposCompletarDadosRef.current = acao
      setCompletandoDados(true)
      return
    }
    acao()
  }

  const respostasPendentesRef = useRef<Record<string, string>>({})

  async function aoConfirmarDadosCompletos({ email, respostas }: { email: string | null; respostas: Record<string, string> }) {
    try {
      if (email && minhaPessoa) {
        await definirEmail.mutateAsync({ pessoaId: minhaPessoa.id, email })
      }
    } catch {
      notificar.erro('Não foi possível salvar o e-mail', 'Confira o e-mail e tente novamente.')
      return
    }
    // Guarda as respostas pra responder assim que a inscrição existir (inscricaoId só
    // nasce depois) — a própria ação (toggle/se inscrever) chama isto no onSuccess dela.
    respostasPendentesRef.current = respostas
    aposCompletarDadosRef.current()
  }

  async function aoInscreverComSucesso(resposta: MinhaInscricaoResponse) {
    const respostas = respostasPendentesRef.current
    respostasPendentesRef.current = {}
    if (resposta.id && Object.keys(respostas).length > 0) {
      const dados = campos.map((c) => ({ campoId: c.id, valor: respostas[c.id] ?? '' }))
      await responder(resposta.id, dados)
    }
    setCompletandoDados(false)
  }

  const modalCompletarDados = completandoDados && (
    <ModalCompletarDadosInscricao
      pedeEmail={!minhaPessoa?.email}
      campos={campos}
      isLoading={definirEmail.isPending || inscrever.isPending}
      onConfirmar={aoConfirmarDadosCompletos}
      onClose={() => setCompletandoDados(false)}
      onFecharTudo={() => setCompletandoDados(false)}
    />
  )

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscrever.isPending}
      onConfirmar={() => inscrever.mutate({ confirmado: true }, {
        onSuccess: async (resposta) => {
          setImpedimentosParaConfirmar(null)
          await aoInscreverComSucesso(resposta)
          if (resposta.cobrancaPendenteId) {
            router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaPendenteId}`)
          } else {
            onInscritoComSucesso?.()
          }
        },
      })}
      onClose={() => setImpedimentosParaConfirmar(null)}
      mensagem={
        <>
          <p>Você não atende a todos os requisitos deste evento:</p>
          <ul>
            {impedimentosParaConfirmar.map((imp) => (
              <li key={imp.codigo}>{imp.mensagem}</li>
            ))}
          </ul>
        </>
      }
    />
  )

  if (isLoading) {
    return (
      <button type="button" className={styles.botao} disabled>
        Carregando…
      </button>
    )
  }

  // Modo "Eu vou": alterna direto, sem diálogo de confirmação
  if (!requerInscricao) {
    const marcado = !!minha?.inscrito

    if (!marcado && inscricaoBloqueadaPelaSituacao) return null

    // Fora de AGENDADO: backend recusa cancelar
    if (marcado && !podeCancelarInscricao(situacao)) {
      return (
        <span className={styles.participou}>
          <CheckCircle2 size={15} aria-hidden="true" />
          Você participou deste evento
        </span>
      )
    }

    const pendente = inscrever.isPending || cancelar.isPending
    // Cancelamento não esbarra em elegibilidade
    const bloqueadoPorImpedimento = !marcado && !!impedimento

    function aoClicarEuVou() {
      if (marcado) {
        if (!minha?.id) return
        cancelar.mutate(minha.id)
      } else {
        tentarInscrever(() => inscrever.mutate({}, { onSuccess: aoInscreverComSucesso }))
      }
    }

    return (
      <span className={styles.euVouWrap}>
        <button
          type="button"
          className={`${styles.euVou} ${marcado ? styles.euVouAtivo : ''}`}
          onClick={aoClicarEuVou}
          disabled={pendente || bloqueadoPorImpedimento}
          aria-pressed={marcado}
        >
          <ThumbsUp size={15} className={styles.icone} aria-hidden="true" />
          {marcado ? 'Você vai' : 'Eu vou'}
        </button>
        {bloqueadoPorImpedimento && (
          <span className={styles.motivo}>
            <AlertTriangle size={13} aria-hidden="true" />
            {impedimento}
          </span>
        )}
        {modalContorno}
        {modalCompletarDados}
      </span>
    )
  }

  // Pagamento em aberto: inscrição existe como AGUARDANDO_PAGAMENTO. Vem de dado do
  // servidor (não de state local), então sobrevive a reload/fechar e reabrir o drawer —
  // ao contrário do antigo `etapaPagamento`, que se perdia ao desmontar o componente.
  // Cobre os três casos que caem neste mesmo estado (evento virou pago, preço aumentou —
  // complemento pendente —, ou checkout iniciado e não terminado): até agora só dava pra
  // cancelar pelo link do e-mail de lembrete; achado ao vivo, 2026-08-27.
  if (!minha?.inscrito && minha?.cobrancaPendenteId) {
    return (
      <div className={styles.pagamentoPendenteBloco}>
        <Link href={`/eventos/${eventoId}/pagamento/${minha.cobrancaPendenteId}`} className={styles.pagamentoPendente}>
          <Clock size={16} aria-hidden="true" />
          <span>Pagamento pendente — continuar</span>
        </Link>
        <button
          type="button"
          className={styles.cancelarLink}
          onClick={() => setConfirmandoCancelamento(true)}
        >
          <XCircle size={14} aria-hidden="true" />
          Cancelar inscrição
        </button>

        {confirmandoCancelamento && (
          <ConfirmarCancelamentoInscricao
            nome=""
            proprio
            quantidadeConvidados={0}
            isLoading={cancelar.isPending}
            onConfirmar={() => {
              if (!minha.id) return
              cancelar.mutate(minha.id, {
                onSuccess: () => setConfirmandoCancelamento(false),
              })
            }}
            onClose={() => setConfirmandoCancelamento(false)}
          />
        )}
      </div>
    )
  }

  if (minha?.inscrito) {
    const podeCancelar = podeCancelarInscricao(situacao)

    return (
      <div className={styles.inscrito}>
        <div className={styles.inscritoStatus}>
          <CheckCircle2 size={18} aria-hidden="true" />
          <div className={styles.inscritoTexto}>
            <strong>Inscrito</strong>
            <span>{podeCancelar ? 'Tudo certo pra você!' : 'Você participou deste evento'}</span>
          </div>
        </div>

        {podeCancelar && (
          <button
            type="button"
            className={styles.cancelarLink}
            onClick={() => setConfirmandoCancelamento(true)}
          >
            <XCircle size={14} aria-hidden="true" />
            Cancelar inscrição
          </button>
        )}

        {confirmandoCancelamento && (
          <ConfirmarCancelamentoInscricao
            nome=""
            proprio
            // Convidado agora é inscrição própria, sem vínculo ao cancelar o titular — a
            // contagem embutida não existe mais (ver Task 10/11) — sem substituto por ora.
            quantidadeConvidados={0}
            isLoading={cancelar.isPending}
            onConfirmar={() => {
              if (!minha.id) return
              cancelar.mutate(minha.id, {
                onSuccess: () => setConfirmandoCancelamento(false),
              })
            }}
            onClose={() => setConfirmandoCancelamento(false)}
          />
        )}
      </div>
    )
  }

  if (inscricaoBloqueadaPelaSituacao || eventoEncerrado) {
    return null
  }

  if (semVagas) {
    return (
      <button type="button" className={styles.botao} disabled>
        Vagas esgotadas
      </button>
    )
  }

  function inscreverDeVerdade() {
    if (!preco) {
      inscrever.mutate({}, {
        onSuccess: async (resposta) => {
          await aoInscreverComSucesso(resposta)
          onInscritoComSucesso?.()
        },
      })
      return
    }
    // Sem conta MP conectada, a rota de checkout nem carregaria (não há pra quem
    // receber) — aviso com atalho em vez de navegar pra uma tela que ia falhar.
    if (!contaPagamento?.conectada) {
      setSemConta(true)
      return
    }
    inscrever.mutate({}, {
      onSuccess: async (resposta) => {
        await aoInscreverComSucesso(resposta)
        if (resposta.cobrancaPendenteId) {
          setNavegandoParaCheckout(true)
          router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaPendenteId}`)
        } else {
          // Não deveria acontecer (evento tem preço), mas não trava a pessoa numa tela morta.
          onInscritoComSucesso?.()
        }
      },
    })
  }

  return (
    <>
      <button
        type="button"
        className={styles.botao}
        disabled={inscrever.isPending || navegandoParaCheckout || !!impedimento}
        onClick={() => tentarInscrever(inscreverDeVerdade)}
      >
        <CheckCircle2 size={18} aria-hidden="true" />
        {inscrever.isPending || navegandoParaCheckout ? 'Inscrevendo…' : 'Se inscrever'}
      </button>

      {impedimento && (
        <span className={styles.motivo}>
          <AlertTriangle size={14} aria-hidden="true" />
          {impedimento}
        </span>
      )}

      {semConta && preco && (
        <div className={styles.avisoSemConta}>
          <AlertTriangle size={16} aria-hidden="true" />
          <span>
            Este evento é pago, mas a igreja ainda não conectou uma conta para receber
            pagamentos.{' '}
            {ehGestor ? (
              <Link href="/configuracoes/igreja">Conectar agora</Link>
            ) : (
              'Fale com a secretaria da igreja.'
            )}
          </span>
          <button type="button" className={styles.cancelarLink} onClick={() => setSemConta(false)}>
            Fechar
          </button>
        </div>
      )}

      {modalContorno}
      {modalCompletarDados}
    </>
  )
}
