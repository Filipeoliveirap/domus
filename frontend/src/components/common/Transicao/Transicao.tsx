'use client'

import { clsx } from 'clsx'
import styles from './Transicao.module.css'

/**
 * Anima a MONTAGEM de um bloco (via `@starting-style` + transition — não re-dispara a cada
 * render). Pra qualquer conteúdo que hoje aparece do nada: resultado de filtro, seção
 * condicional, painel que expande, preview.
 */
export function Transicao({
  children,
  modo = 'fade',
  className,
  ref,
}: {
  children: React.ReactNode
  modo?: 'fade' | 'subir' | 'escala'
  className?: string
  ref?: React.Ref<HTMLDivElement>
}) {
  return <div ref={ref} className={clsx(styles.bloco, styles[modo], className)}>{children}</div>
}
