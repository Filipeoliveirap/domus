'use client'

import { useEffect } from 'react'
import { X, Ticket, Phone, Mail } from 'lucide-react'
import { useMinhaIgreja } from '@/hooks/igreja/useMinhaIgreja'
import { Button } from '@/components/common/button/Button'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { formatarTelefoneExibicao } from '@/lib/formats/membroFormat'
import styles from './ModalConvidado.module.css'

interface Props {
  /** Número, como a API serializa BigDecimal. `formatarMoeda` aceita os dois. */
  preco: number
  isLoading: boolean
  onConfirmar: () => void
  onClose: () => void
}

/**
 * F3 — antes de confirmar presença num evento pago, mostra o valor e como combinar o
 * pagamento. Só confirma de fato quando a pessoa segue em frente.
 *
 * <p>O responsável pelo evento ainda não existe como conceito (feature futura) — por isso
 * o contato mostrado aqui é o da PRÓPRIA IGREJA (telefone/e-mail de `useMinhaIgreja`), não
 * de uma pessoa. Melhor um contato real da igreja do que um "fale com ___" vazio.
 */
export function ModalConfirmarPagamento({ preco, isLoading, onConfirmar, onClose }: Props) {
  const { data: igreja } = useMinhaIgreja()

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, isLoading])

  const telefone = igreja?.telefoneContato ? formatarTelefoneExibicao(igreja.telefoneContato) : null
  const email = igreja?.emailContato ?? null
  const temContato = !!telefone || !!email

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-confirmar-pagamento"
      >
        <div className={styles.header}>
          <div>
            <h2 className={styles.titulo} id="titulo-confirmar-pagamento">Este evento é pago</h2>
            <p className={styles.subtitulo}>
              O Domus não processa o pagamento — combine diretamente com a igreja.
            </p>
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

        <div className={styles.form}>
          <div className={styles.infoBox}>
            <Ticket size={18} className={styles.infoIcon} aria-hidden="true" />
            <p className={styles.infoText}>
              Valor: <strong>{formatarMoeda(preco)}</strong> por pessoa.
            </p>
          </div>

          <div className={styles.infoBox}>
            {telefone ? <Phone size={18} className={styles.infoIcon} aria-hidden="true" /> : <Mail size={18} className={styles.infoIcon} aria-hidden="true" />}
            <div className={styles.infoText}>
              {temContato ? (
                <>
                  <p>Fale com a igreja para combinar o pagamento:</p>
                  {telefone && <p><strong>Telefone:</strong> {telefone}</p>}
                  {email && <p><strong>E-mail:</strong> {email}</p>}
                </>
              ) : (
                <p>Procure a secretaria da igreja para combinar o pagamento.</p>
              )}
            </div>
          </div>

          <div className={styles.footer}>
            <button type="button" className={styles.btnCancelar} onClick={onClose} disabled={isLoading}>
              Cancelar
            </button>
            <Button type="button" variant="primary" size="md" isLoading={isLoading} onClick={onConfirmar}>
              Confirmar presença
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
