'use client'

import { Check } from 'lucide-react'
import { useUiStore } from '@/store/uiStore'
import styles from './PonteParaCheckout.module.css'

/**
 * Overlay curto entre "a inscrição foi criada" e a rota de checkout abrir. A navegação sai
 * de dentro do app shell (com sidebar/drawer) pra uma rota full-screen — sem esta ponte o
 * corte é seco. O estado vive no `uiStore` (não num componente que a navegação desmonta) e
 * é montado uma vez no layout do app; o `loading.tsx` da rota de checkout assume em seguida.
 */
export function PonteParaCheckout() {
  const ativo = useUiStore((s) => s.ponteCheckout)

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
      <span className={styles.sub}>Abrindo o pagamento…</span>
    </div>
  )
}
