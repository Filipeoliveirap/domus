'use client'

import { useState } from 'react'
import Link from 'next/link'
import { useRouter } from 'next/navigation'
import { CheckCircle2, XCircle, ThumbsUp, AlertTriangle, Clock } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { useElegibilidade } from '@/hooks/inscricao/useElegibilidade'
import { useContaPagamento } from '@/hooks/pagamento/useContaPagamento'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarInscricoes } from '@/lib/permissoes'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import type { SituacaoEvento } from '@/types/evento.type'
import type { Impedimento } from '@/types/inscricao.type'
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

  const role = useAuthStore((s) => s.role)
  // Gestor ignora restrições com confirmação extra
  const ehGestor = podeGerenciarInscricoes(role)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  // Status da conta MP da própria igreja — só importa quando o evento é pago.
  const { data: contaPagamento } = useContaPagamento()
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

  function aoConfirmarMesmoAssim() {
    inscrever.mutate({ confirmado: true }, {
      onSuccess: (resposta) => {
        setImpedimentosParaConfirmar(null)
        if (resposta.cobrancaPendenteId) {
          router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaPendenteId}`)
        } else {
          onInscritoComSucesso?.()
        }
      },
    })
  }

  const modalContorno = impedimentosParaConfirmar && (
    <ModalConfirmacao
      titulo="Inscrever mesmo assim?"
      textoConfirmar="Inscrever mesmo assim"
      isLoading={inscrever.isPending}
      onConfirmar={aoConfirmarMesmoAssim}
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
        inscrever.mutate({})
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
      </span>
    )
  }

  // Pagamento em aberto: inscrição existe como AGUARDANDO_PAGAMENTO. Vem de dado do
  // servidor (não de state local), então sobrevive a reload/fechar e reabrir o drawer —
  // ao contrário do antigo `etapaPagamento`, que se perdia ao desmontar o componente.
  if (!minha?.inscrito && minha?.cobrancaPendenteId) {
    return (
      <Link href={`/eventos/${eventoId}/pagamento/${minha.cobrancaPendenteId}`} className={styles.pagamentoPendente}>
        <Clock size={16} aria-hidden="true" />
        <span>Pagamento pendente — continuar</span>
      </Link>
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
            quantidadeConvidados={minha.acompanhantes.length}
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

  return (
    <>
      <button
        type="button"
        className={styles.botao}
        disabled={inscrever.isPending || navegandoParaCheckout || !!impedimento}
        onClick={() => {
          if (!preco) {
            inscrever.mutate({}, { onSuccess: onInscritoComSucesso })
            return
          }
          // Sem conta MP conectada, a rota de checkout nem carregaria (não há pra quem
          // receber) — aviso com atalho em vez de navegar pra uma tela que ia falhar.
          if (!contaPagamento?.conectada) {
            setSemConta(true)
            return
          }
          inscrever.mutate({}, {
            onSuccess: (resposta) => {
              if (resposta.cobrancaPendenteId) {
                setNavegandoParaCheckout(true)
                router.push(`/eventos/${eventoId}/pagamento/${resposta.cobrancaPendenteId}`)
              } else {
                // Não deveria acontecer (evento tem preço), mas não trava a pessoa numa tela morta.
                onInscritoComSucesso?.()
              }
            },
          })
        }}
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
    </>
  )
}
