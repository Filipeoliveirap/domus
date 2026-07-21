'use client'

import { useState } from 'react'
import { CheckCircle2, XCircle } from 'lucide-react'
import { useMinhaInscricao } from '@/hooks/inscricao/useMinhaInscricao'
import { useInscrever } from '@/hooks/inscricao/useInscrever'
import { useCancelarInscricao } from '@/hooks/inscricao/useCancelarInscricao'
import styles from './BotaoConfirmarPresenca.module.css'

interface Props {
  eventoId: string
  /** ISO do início do evento — evita reconsultar o evento só para saber se já passou. */
  inicioEm: string
  /** `null` = evento sem limite de vagas. */
  vagasRestantes: number | null
}

/**
 * Botão de inscrição no evento, com todos os estados possíveis na ordem de prioridade
 * que faz sentido mostrar para quem olha a tela: primeiro "já sei que está carregando",
 * depois "você já está inscrito" (o estado mais comum de quem volta à tela), só então os
 * bloqueios (encerrado / esgotado), e por último a ação principal.
 */
export function BotaoConfirmarPresenca({ eventoId, inicioEm, vagasRestantes }: Props) {
  const [confirmandoCancelamento, setConfirmandoCancelamento] = useState(false)

  const { data: minha, isLoading } = useMinhaInscricao(eventoId)
  const inscrever = useInscrever(eventoId)
  const cancelar = useCancelarInscricao()

  const eventoEncerrado = new Date(inicioEm) < new Date()
  const semVagas = vagasRestantes !== null && vagasRestantes <= 0

  if (isLoading) {
    return (
      <button type="button" className={styles.botao} disabled>
        Carregando…
      </button>
    )
  }

  if (minha?.inscrito) {
    return (
      <div className={styles.inscrito}>
        <div className={styles.inscritoStatus}>
          <CheckCircle2 size={18} aria-hidden="true" />
          <div className={styles.inscritoTexto}>
            <strong>Inscrito</strong>
            <span>Tudo certo pra você!</span>
          </div>
        </div>

        {confirmandoCancelamento ? (
          <div className={styles.confirmacaoInline}>
            <span>Cancelar sua inscrição?</span>
            <div className={styles.confirmacaoAcoes}>
              <button
                type="button"
                className={styles.confirmacaoNao}
                onClick={() => setConfirmandoCancelamento(false)}
                disabled={cancelar.isPending}
              >
                Não
              </button>
              <button
                type="button"
                className={styles.confirmacaoSim}
                onClick={() => {
                  if (!minha.id) return
                  cancelar.mutate(minha.id, {
                    onSuccess: () => setConfirmandoCancelamento(false),
                  })
                }}
                disabled={cancelar.isPending}
              >
                {cancelar.isPending ? 'Cancelando…' : 'Sim, cancelar'}
              </button>
            </div>
          </div>
        ) : (
          <button
            type="button"
            className={styles.cancelarLink}
            onClick={() => setConfirmandoCancelamento(true)}
          >
            <XCircle size={14} aria-hidden="true" />
            Cancelar inscrição
          </button>
        )}
      </div>
    )
  }

  if (eventoEncerrado) {
    return (
      <button type="button" className={styles.botao} disabled>
        Evento encerrado
      </button>
    )
  }

  if (semVagas) {
    return (
      <button type="button" className={styles.botao} disabled>
        Vagas esgotadas
      </button>
    )
  }

  return (
    <button
      type="button"
      className={styles.botao}
      disabled={inscrever.isPending}
      onClick={() => inscrever.mutate()}
    >
      <CheckCircle2 size={18} aria-hidden="true" />
      {inscrever.isPending ? 'Confirmando…' : 'Confirmar presença'}
    </button>
  )
}
