'use client'

import { useEffect, useRef } from 'react'
import { AlertTriangle } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import styles from './ModalConfirmacao.module.css'

interface Props {
  titulo: string
  mensagem: React.ReactNode
  textoConfirmar?: string
  textoCancelar?: string
  perigo?: boolean
  isLoading?: boolean
  onConfirmar: () => void
  onClose: () => void
}

export function ModalConfirmacao({
  titulo, mensagem, textoConfirmar = 'Confirmar', textoCancelar = 'Cancelar',
  perigo = false, isLoading = false, onConfirmar, onClose,
}: Props) {
  const confirmarRef = useRef<HTMLButtonElement>(null)
  const { saindo, fechar } = useFecharAnimado(onClose)

  useEffect(() => {
    confirmarRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, isLoading])

  return (
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      onMouseDown={() => !isLoading && fechar()}
    >
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-confirmacao-titulo"
      >
        <div className={styles.cabecalho}>
          <span className={`${styles.iconBox} ${perigo ? styles.iconPerigo : ''}`}>
            <AlertTriangle size={22} aria-hidden="true" />
          </span>
          <h2 className={styles.titulo} id="modal-confirmacao-titulo">{titulo}</h2>
        </div>

        <div className={styles.corpo}>{mensagem}</div>

        <div className={styles.rodape}>
          <button
            type="button"
            className={styles.btnCancelar}
            onClick={fechar}
            disabled={isLoading}
          >
            {textoCancelar}
          </button>
          <button
            ref={confirmarRef}
            type="button"
            className={`${styles.btnConfirmar} ${perigo ? styles.btnPerigo : ''}`}
            onClick={onConfirmar}
            disabled={isLoading}
          >
            {isLoading ? 'Processando…' : textoConfirmar}
          </button>
        </div>
      </div>
    </div>
  )
}
