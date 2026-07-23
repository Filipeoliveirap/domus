'use client'

import { useEffect, useRef } from 'react'
import { AlertTriangle } from 'lucide-react'
import styles from './ModalConfirmacao.module.css'

interface Props {
  titulo: string
  /** Corpo da confirmação. Pode conter blocos (lista de motivos etc.) — é renderizado num
   *  `<div>`, não num `<p>`, então `<ul>`/`<p>` dentro são HTML válido. */
  mensagem: React.ReactNode
  textoConfirmar?: string
  textoCancelar?: string
  /** Realça o botão de confirmar como ação de risco (vermelho) em vez da cor primária. */
  perigo?: boolean
  isLoading?: boolean
  onConfirmar: () => void
  onClose: () => void
}

/**
 * Confirmação simples de sim/não — para decisões reversíveis ou de baixo risco, onde
 * pedir para digitar uma palavra (como o {@link ModalConfirmacaoCritica}) seria atrito à
 * toa. Ex.: "inscrever mesmo assim?" quando um gestor contorna uma restrição.
 *
 * <p>A mensagem entra num `<div>` (não num `<p>`), então pode conter lista/parágrafos sem
 * gerar HTML inválido — foi o que quebrava a hidratação quando uma `<ul>` ia parar dentro
 * do `<p>` do modal crítico.
 */
export function ModalConfirmacao({
  titulo, mensagem, textoConfirmar = 'Confirmar', textoCancelar = 'Cancelar',
  perigo = false, isLoading = false, onConfirmar, onClose,
}: Props) {
  const confirmarRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    confirmarRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, isLoading])

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
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
            onClick={onClose}
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
