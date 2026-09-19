'use client'

import { Check } from 'lucide-react'
import { useUiStore } from '@/store/uiStore'
import styles from './PonteParaCheckout.module.css'

/**
 * Overlay curto mostrado logo após qualquer "Se inscrever" bem-sucedido (grátis ou pago).
 * No evento pago dobra de ponte até a rota de checkout abrir (fora do app shell, sem
 * sidebar/drawer) — sem ela o corte de rota seria seco; no grátis só confirma a ação antes
 * do drawer/modal fechar. O estado vive no `uiStore` (não num componente que a ação
 * desmonta) e é montado uma vez no layout do app.
 */
export function PonteParaCheckout() {
  const ativo = useUiStore((s) => s.ponteCheckout)
  const subtitulo = useUiStore((s) => s.ponteSubtitulo)

  return (
    <div
      className={`${styles.veu} ${ativo ? styles.visivel : ''}`}
      role="status"
      aria-live="polite"
      aria-hidden={!ativo}
    >
      <span className={styles.selo}>
        <span className={styles.anel} aria-hidden="true" />
        <Check size={28} strokeWidth={3} aria-hidden="true" />
      </span>
      <span className={styles.titulo}>Inscrição feita!</span>
      {subtitulo && <span className={styles.sub}>{subtitulo}</span>}
    </div>
  )
}
