'use client'

import { clsx } from 'clsx'
import styles from './Loader.module.css'

type Size = 'sm' | 'md' | 'lg'

export interface LoaderProps {
  /** Só existe 'circular' — mantido como prop pra chamadas ficarem explícitas. */
  variant?: 'circular'
  size?: Size
  className?: string
}

const CARREGANDO = 'Carregando'

export function CircularLoader({ className, size = 'md' }: { className?: string; size?: Size }) {
  return (
    <span className={clsx(styles.circular, styles[size], className)} role="status">
      <span className={styles.srOnly}>{CARREGANDO}</span>
    </span>
  )
}

export function Loader({ size = 'md', className }: LoaderProps) {
  return <CircularLoader size={size} className={className} />
}
