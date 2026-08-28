'use client'

import { createPortal } from 'react-dom'
import { CreditCard, Send } from 'lucide-react'
import { useState, useEffect } from 'react'
import { useEnviarLembretePagamento } from '@/hooks/inscricao/useEnviarLembretePagamento'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './PendenciaCamposBadge.module.css'

/** Fica ao lado do nome de quem está com a inscrição AGUARDANDO_PAGAMENTO — clicar abre
 *  um modal que oferece mandar um lembrete por e-mail (nunca chamado de "cobrança", nem
 *  aqui nem no e-mail em si — é um empurrão gentil, não uma régua de cobrança).
 *
 *  `pagamentoParcial` distingue duas situações bem diferentes que compartilham o mesmo
 *  status (achado ao vivo, 2026-08-27 — misturar as duas confundia o admin): `false` é
 *  quem nunca pagou nada ainda; `true` é quem já pagou o valor original e só falta a
 *  diferença de um reajuste de preço do evento. */
export function PagamentoPendenteBadge({
  nome, eventoId, inscricaoId, pagamentoParcial,
}: { nome: string; eventoId: string; inscricaoId: string; pagamentoParcial: boolean }) {
  const [aberto, setAberto] = useState(false)

  return (
    <>
      <button
        type="button"
        className={styles.badge}
        onClick={(e) => { e.stopPropagation(); setAberto(true) }}
      >
        <CreditCard size={12} aria-hidden="true" />
        {pagamentoParcial ? 'Falta complementar' : 'Pagamento pendente'}
      </button>

      {aberto && (
        <ModalLembrete
          nome={nome}
          eventoId={eventoId}
          inscricaoId={inscricaoId}
          pagamentoParcial={pagamentoParcial}
          onClose={() => setAberto(false)}
        />
      )}
    </>
  )
}

function ModalLembrete({
  nome, eventoId, inscricaoId, pagamentoParcial, onClose,
}: { nome: string; eventoId: string; inscricaoId: string; pagamentoParcial: boolean; onClose: () => void }) {
  const lembrete = useEnviarLembretePagamento(eventoId)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !lembrete.isPending) onClose() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, lembrete.isPending])

  if (typeof document === 'undefined') return null

  // stopPropagation em todo clique: mesmo motivo do PendenciaCamposBadge — o portal
  // continua descendente da linha clicável na árvore do React, e é essa árvore que decide
  // bubbling de evento sintético, não o DOM real.
  return createPortal(
    <div
      className={baseStyles.overlay}
      onClick={(e) => { e.stopPropagation(); if (!lembrete.isPending) onClose() }}
    >
      <div
        className={baseStyles.modal}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-lembrete-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <CreditCard size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-lembrete-titulo">
            {pagamentoParcial ? `Falta complementar o pagamento de ${nome}` : `Pagamento pendente de ${nome}`}
          </h2>
        </div>

        <div className={baseStyles.corpo}>
          {pagamentoParcial
            ? `${nome} já pagou o valor original da inscrição — só falta a diferença de um reajuste de preço do evento.`
            : `A inscrição de ${nome} está aguardando pagamento.`}
          {' '}Você pode enviar um lembrete por e-mail, com o link pra efetuar o pagamento.
        </div>

        <div className={baseStyles.rodape}>
          <button
            type="button"
            className={baseStyles.btnCancelar}
            onClick={(e) => { e.stopPropagation(); onClose() }}
            disabled={lembrete.isPending}
          >
            Fechar
          </button>
          <button
            type="button"
            className={baseStyles.btnConfirmar}
            disabled={lembrete.isPending}
            onClick={(e) => {
              e.stopPropagation()
              lembrete.mutate(inscricaoId, { onSuccess: onClose })
            }}
          >
            <Send size={14} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
            {lembrete.isPending ? 'Enviando…' : 'Enviar lembrete'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
