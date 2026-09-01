'use client'

import { useEffect } from 'react'
import { clsx } from 'clsx'
import { Archive, History, Info, type LucideIcon } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
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
  /** Texto do botão de confirmar enquanto a ação está em andamento. */
  textoCarregando?: string
  reversivel?: boolean
  /** Ícone do cabeçalho — permite reusar este modal para confirmações leves não ligadas a arquivamento (ex.: A11). */
  icone?: LucideIcon
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
  textoCarregando = 'Arquivando…',
  reversivel = true,
  icone: Icone = Archive,
}: ModalArquivarProps) {
  const { saindo, fechar } = useFecharAnimado(onClose, 200)

  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) fechar()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [fechar, isLoading])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={() => !isLoading && fechar()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className={styles.corpo}>
          <div className={styles.iconBox}>
            <Icone size={24} />
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
            onClick={fechar}
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
            {isLoading ? textoCarregando : textoConfirmar}
          </button>
        </div>
      </div>
    </div>
  )
}
