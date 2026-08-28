'use client'

import { clsx } from 'clsx'
import styles from './Transicao.module.css'

/**
 * Item de lista que entra com fade/slide (`@starting-style`) e, quando `saindo` vira true,
 * roda a transição reversa + colapsa a altura antes de o pai desmontar. Combine com
 * `useListaComSaida` pra segurar o item no array durante a saída.
 */
export function ItemAnimado({
  children,
  saindo = false,
  className,
}: {
  children: React.ReactNode
  saindo?: boolean
  className?: string
}) {
  return <div className={clsx(styles.item, saindo && styles.itemSaindo, className)}>{children}</div>
}
