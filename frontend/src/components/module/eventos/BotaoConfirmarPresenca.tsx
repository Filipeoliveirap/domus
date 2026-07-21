'use client'

import { useState } from 'react'
import { CheckCircle2, XCircle, ThumbsUp } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import { ModalConfirmarPagamento } from './ModalConfirmarPagamento'
import { ConfirmarCancelamentoInscricao } from './ConfirmarCancelamentoInscricao'
import { podeCancelarInscricao } from '@/lib/formats/eventoFormat'
import type { SituacaoEvento } from '@/types/evento.type'
import styles from './BotaoConfirmarPresenca.module.css'

interface Props {
  eventoId: string
  /** ISO do início do evento — evita reconsultar o evento só para saber se já passou. */
  inicioEm: string
  /** `null` = evento sem limite de vagas. */
  vagasRestantes: number | null
  /**
   * `false` = evento casual (culto etc.): a presença é só um sinal social leve ("curtida"),
   * sem inscrever outras pessoas nem convidados — o botão fica discreto e alterna direto,
   * sem confirmação de cancelamento. `true` = fluxo formal de inscrição (retiro etc.).
   */
  requerInscricao: boolean
  /**
   * F15: situação real do evento (backend). Fora de AGENDADO o backend recusa qualquer
   * nova inscrição — mostrar um botão de ação aqui só ofereceria algo que só pode falhar,
   * então a CTA nem aparece (quem já está inscrito continua vendo o próprio status).
   */
  situacao: SituacaoEvento
  /** F3: quando o evento é pago, confirmar presença abre um passo intermediário antes de registrar. */
  /** Número no JSON da API (BigDecimal do Jackson). `null`/ausente = evento gratuito. */
  preco?: number | null
}

/**
 * Botão de inscrição no evento, com todos os estados possíveis na ordem de prioridade
 * que faz sentido mostrar para quem olha a tela: primeiro "já sei que está carregando",
 * depois "você já está inscrito" (o estado mais comum de quem volta à tela), só então os
 * bloqueios (encerrado / esgotado), e por último a ação principal.
 */
export function BotaoConfirmarPresenca({ eventoId, inicioEm, vagasRestantes, requerInscricao, situacao, preco }: Props) {
  const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false)
  const [mostrarModalPagamento, setMostrarModalPagamento] = useState(false)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever(eventoId)
  const cancelar = useCancelarInscricao()

  const eventoEncerrado = new Date(inicioEm) < new Date()
  const semVagas = vagasRestantes !== null && vagasRestantes <= 0
  // F15: fora de AGENDADO o backend recusa inscrição — a CTA de registrar não aparece.
  // Quem já está inscrito continua vendo o próprio status (isso não é "se inscrever").
  const inscricaoBloqueadaPelaSituacao = situacao !== 'AGENDADO'

  if (isLoading) {
    return (
      <button type="button" className={styles.botao} disabled>
        Carregando…
      </button>
    )
  }

  // F1 — modo "Eu vou": curtida, não CTA formal. Alterna direto, sem passo de confirmação.
  if (!requerInscricao) {
    const marcado = !!minha?.inscrito

    // F15: sem marcação prévia, e evento em andamento/encerrado — o botão "Eu vou" some.
    if (!marcado && inscricaoBloqueadaPelaSituacao) return null

    // A2/rodada 3: o backend recusa cancelar fora de AGENDADO — quem já confirmou presença
    // num evento em andamento/encerrado vê o registro como histórico, não como algo reversível.
    if (marcado && !podeCancelarInscricao(situacao)) {
      return (
        <span className={styles.participou}>
          <CheckCircle2 size={15} aria-hidden="true" />
          Você participou deste evento
        </span>
      )
    }

    const pendente = inscrever.isPending || cancelar.isPending

    function aoClicar() {
      if (marcado) {
        if (!minha?.id) return
        cancelar.mutate(minha.id)
      } else {
        inscrever.mutate()
      }
    }

    return (
      <button
        type="button"
        className={`${styles.euVou} ${marcado ? styles.euVouAtivo : ''}`}
        onClick={aoClicar}
        disabled={pendente}
        aria-pressed={marcado}
      >
        <ThumbsUp size={15} aria-hidden="true" />
        {marcado ? 'Você vai' : 'Eu vou'}
      </button>
    )
  }

  if (minha?.inscrito) {
    // A2/rodada 3: fora de AGENDADO o backend recusa o cancelamento — sem exceção — então
    // a ação nem aparece; o que resta é reconhecer que a presença já aconteceu.
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

  // F15: fora de AGENDADO, nem mostra a CTA — o backend recusaria do mesmo jeito.
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
        disabled={inscrever.isPending}
        onClick={() => (preco ? setMostrarModalPagamento(true) : inscrever.mutate())}
      >
        <CheckCircle2 size={18} aria-hidden="true" />
        {inscrever.isPending ? 'Confirmando…' : 'Confirmar presença'}
      </button>

      {mostrarModalPagamento && preco && (
        <ModalConfirmarPagamento
          preco={preco}
          isLoading={inscrever.isPending}
          onConfirmar={() => inscrever.mutate(undefined, { onSuccess: () => setMostrarModalPagamento(false) })}
          onClose={() => setMostrarModalPagamento(false)}
        />
      )}
    </>
  )
}
