'use client'

import { useEffect } from 'react'
import { Archive, History, Info } from 'lucide-react'
import styles from './ModalArquivar.module.css'

interface ModalArquivarProps {
  titulo: string
  mensagem: React.ReactNode
  aviso?: React.ReactNode
  onConfirmar: () => void
  onClose: () => void
  isLoading?: boolean
  erro?: string | null
  textoConfirmar?: string
  reversivel?: boolean
}

export function ModalArquivar({
  titulo,
  mensagem,
  aviso,
  onConfirmar,
  onClose,
  isLoading = false,
  erro = null,
  textoConfirmar = 'Confirmar arquivamento',
  reversivel = true,
}: ModalArquivarProps) {
  
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [onClose, isLoading])

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className={styles.corpo}>
          <div className={styles.iconBox}>
            <Archive size={28} />
          </div>

          <h2 className={styles.titulo}>{titulo}</h2>

          <p className={styles.mensagem}>{mensagem}</p>

          {reversivel && (
            <span className={styles.badge}>
              <History size={14} />
              Ação reversível
            </span>
          )}
        </div>

        {aviso && (
          <div className={styles.aviso}>
            <Info size={18} className={styles.avisoIcon} />
            <p className={styles.avisoText}>{aviso}</p>
          </div>
        )}

        {erro && <div className={styles.erro}>{erro}</div>}

        <div className={styles.rodape}>
          <button
            type="button"
            className={styles.btnCancelar}
            onClick={onClose}
            disabled={isLoading}
          >
            Cancelar
          </button>
          <button
            type="button"
            className={styles.btnConfirmar}
            onClick={onConfirmar}
            disabled={isLoading}
          >
            {isLoading ? 'Arquivando…' : textoConfirmar}
          </button>
        </div>
      </div>
    </div>
  )
}