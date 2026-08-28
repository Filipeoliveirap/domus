'use client'

import { createPortal } from 'react-dom'
import { AlertTriangle, RotateCcw } from 'lucide-react'
import { useState, useEffect } from 'react'
import { useTentarEstornoNovamente } from '@/hooks/inscricao/useTentarEstornoNovamente'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './PendenciaCamposBadge.module.css'

/** Fica ao lado do nome de quem tem uma cobrança com estorno pendente (2026-08-27) —
 *  achado ao vivo: estorno em massa (evento virou gratuito, preço baixou, arquivamento de
 *  evento, remoção de não-elegível...) que falha no Mercado Pago não podia ser tentado de
 *  novo — a pessoa ficava sem o dinheiro de volta e ninguém via isso na tela. Clicar abre
 *  um modal que tenta o MESMO estorno de novo (POST /cobrancas/{id}/tentar-estorno-novamente,
 *  ADMIN/LÍDER). Diferente de {@link PagamentoPendenteBadge}, pode aparecer em qualquer
 *  status (CONFIRMADA ou AGUARDANDO_PAGAMENTO) — o estorno pendente não depende de a
 *  inscrição estar aguardando pagamento. */
export function EstornoPendenteBadge({
  nome, eventoId, cobrancaId,
}: { nome: string; eventoId: string; cobrancaId: string }) {
  const [aberto, setAberto] = useState(false)

  return (
    <>
      <button
        type="button"
        className={styles.badge}
        style={{ background: 'var(--color-danger-bg)', color: 'var(--color-danger)' }}
        onClick={(e) => { e.stopPropagation(); setAberto(true) }}
      >
        <AlertTriangle size={12} aria-hidden="true" />
        Estorno pendente
      </button>

      {aberto && (
        <ModalRetryEstorno
          nome={nome}
          eventoId={eventoId}
          cobrancaId={cobrancaId}
          onClose={() => setAberto(false)}
        />
      )}
    </>
  )
}

function ModalRetryEstorno({
  nome, eventoId, cobrancaId, onClose,
}: { nome: string; eventoId: string; cobrancaId: string; onClose: () => void }) {
  const retry = useTentarEstornoNovamente(eventoId)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => { if (e.key === 'Escape' && !retry.isPending) onClose() }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, retry.isPending])

  if (typeof document === 'undefined') return null

  return createPortal(
    <div
      className={baseStyles.overlay}
      onClick={(e) => { e.stopPropagation(); if (!retry.isPending) onClose() }}
    >
      <div
        className={baseStyles.modal}
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-retry-estorno-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <AlertTriangle size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-retry-estorno-titulo">
            Estorno pendente de {nome}
          </h2>
        </div>

        <div className={baseStyles.corpo}>
          Uma tentativa anterior de estornar o pagamento de {nome} falhou no Mercado Pago.
          Você pode tentar de novo agora.
        </div>

        <div className={baseStyles.rodape}>
          <button
            type="button"
            className={baseStyles.btnCancelar}
            onClick={(e) => { e.stopPropagation(); onClose() }}
            disabled={retry.isPending}
          >
            Fechar
          </button>
          <button
            type="button"
            className={baseStyles.btnConfirmar}
            disabled={retry.isPending}
            onClick={(e) => {
              e.stopPropagation()
              retry.mutate(cobrancaId, { onSuccess: onClose })
            }}
          >
            <RotateCcw size={14} aria-hidden="true" style={{ marginRight: 6, verticalAlign: 'text-bottom' }} />
            {retry.isPending ? 'Estornando…' : 'Tentar estornar de novo'}
          </button>
        </div>
      </div>
    </div>,
    document.body,
  )
}
