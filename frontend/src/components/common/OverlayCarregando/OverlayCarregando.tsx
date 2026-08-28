'use client'

import { clsx } from 'clsx'
import { Loader } from '@/components/common/Loader/Loader'
import styles from './OverlayCarregando.module.css'

interface OverlayCarregandoProps {
  ativo: boolean
  /** Texto abaixo do spinner (ex.: "Processando pagamento…"). Opcional. */
  texto?: string
  /** 'fixed' cobre a viewport toda (default); 'absolute' cobre só o container-pai
   *  posicionado — use dentro de um modal/card com `position: relative`. */
  cobertura?: 'fixed' | 'absolute'
}

/**
 * Véu + spinner pra operação BLOQUEANTE (pagar, excluir, submeter cadastro) — clicar em
 * outro lugar no meio quebra a ação. Não use pra navegação de rota (essa nunca bloqueia —
 * é o <NavProgress>).
 */
export function OverlayCarregando({ ativo, texto, cobertura = 'fixed' }: OverlayCarregandoProps) {
  return (
    <div
      className={clsx(styles.overlay, styles[cobertura], ativo && styles.visivel)}
      role="status"
      aria-live="polite"
      aria-hidden={!ativo}
    >
      <span className={styles.spinner}>
        <Loader variant="circular" size="lg" />
      </span>
      {texto && <span className={styles.texto}>{texto}</span>}
    </div>
  )
}
