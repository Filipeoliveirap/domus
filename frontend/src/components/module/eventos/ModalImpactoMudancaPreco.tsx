'use client'

import { useEffect } from 'react'
import { X, AlertTriangle } from 'lucide-react'
import { Button } from '@/components/common/button/Button'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { ImpactoMudancaPrecoResponse } from '@/types/evento.type'
import styles from './ModalImpactoRestricao.module.css'

interface Props {
  impacto: ImpactoMudancaPrecoResponse
  isLoading: boolean
  onConfirmar: () => void
  onClose: () => void
}

/**
 * Aviso antes de confirmar um evento pago virando gratuito com gente já
 * inscrita — mexe com dinheiro de verdade (estorno real no Mercado Pago), então mostra
 * os números antes do admin apertar "Salvar" de vez. Mesma linguagem visual de
 * ModalImpactoRestricao (reaproveita o CSS module dele).
 */
export function ModalImpactoMudancaPreco({ impacto, isLoading, onConfirmar, onClose }: Props) {
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, isLoading])

  const { pessoasComPagamentoPago, valorTotalAEstornar, pessoasAguardandoPagamento } = impacto

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-impacto-mudanca-preco"
      >
        <div className={styles.header}>
          <div className={styles.headerTexto}>
            <span className={styles.iconBox}>
              <AlertTriangle size={20} aria-hidden="true" />
            </span>
            <div>
              <h2 className={styles.titulo} id="titulo-impacto-mudanca-preco">
                Este evento vai virar gratuito
              </h2>
              <p className={styles.subtitulo}>
                {pessoasComPagamentoPago > 0 && (
                  <>
                    {pessoasComPagamentoPago === 1 ? '1 pessoa já pagou' : `${pessoasComPagamentoPago} pessoas já pagaram`}
                    {' — '}<strong>{formatarMoeda(valorTotalAEstornar)}</strong> serão estornados. As inscrições permanecerão.
                    {pessoasAguardandoPagamento > 0 && ' '}
                  </>
                )}
                {pessoasAguardandoPagamento > 0 && (
                  <>
                    {pessoasAguardandoPagamento === 1 ? '1 pessoa está' : `${pessoasAguardandoPagamento} pessoas estão`}
                    {' '}aguardando pagamento — a inscrição será confirmada direto, sem cobrar nada.
                  </>
                )}
              </p>
            </div>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={isLoading}
          >
            <X size={20} />
          </button>
        </div>

        <div className={styles.footer}>
          <Button type="button" variant="secondary" size="md" isLoading={false} disabled={isLoading} onClick={onClose}>
            Cancelar
          </Button>
          <Button type="button" variant="primary" size="md" isLoading={isLoading} onClick={onConfirmar}>
            Confirmar mudança
          </Button>
        </div>
      </div>
    </div>
  )
}
