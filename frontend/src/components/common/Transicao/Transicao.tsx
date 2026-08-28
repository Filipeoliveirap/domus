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
}: {
  children: React.ReactNode
  modo?: 'fade' | 'subir' | 'escala'
  className?: string
}) {
  return <div className={clsx(styles.bloco, styles[modo], className)}>{children}</div>
}
