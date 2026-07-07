'use client'
import styles from './ProgressIndicator.module.css'

interface ProgressIndicatorProps {
  passoAtual: number
  totalPassos: number
  labelDireita?: string 
}

export function ProgressIndicator({ passoAtual, totalPassos, labelDireita }: ProgressIndicatorProps) {
  const barras = Array.from({ length: totalPassos }, (_, i) => i + 1)
  return (
    <div className={styles.container} role="progressbar"
      aria-valuenow={passoAtual} aria-valuemin={1} aria-valuemax={totalPassos}>
      <div className={styles.topo}>
        <span className={styles.label}>PASSO {passoAtual} DE {totalPassos}</span>
        {labelDireita && <span className={styles.labelDireita}>{labelDireita}</span>}
      </div>
      <div className={styles.barras}>
        {barras.map((numero) => (
          <div
            key={numero}
            className={`${styles.bar} ${numero <= passoAtual ? styles.barActive : ''}`}
          />
        ))}
      </div>
    </div>
  )
}