'use client'

import styles from './ProgressIndicator.module.css'

interface ProgressIndicatorProps {
  passoAtual: number
  totalPassos: number
}

export function ProgressIndicator({ passoAtual, totalPassos }: ProgressIndicatorProps) {
  const barras = Array.from({ length: totalPassos }, (_, i) => i + 1)

  return (
    <div className={styles.container} role="progressbar"
      aria-valuenow={passoAtual} aria-valuemin={1} aria-valuemax={totalPassos}>

      {barras.map((numero) => (
        <div
          key={numero}
          className={`${styles.bar} ${numero <= passoAtual ? styles.barActive : ''}`}
        />
      ))}

      <span className={styles.label}>
        PASSO {passoAtual} DE {totalPassos}
      </span>

    </div>
  )
}