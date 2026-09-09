'use client'

import { clsx } from 'clsx'
import styles from './Colapsavel.module.css'

/**
 * Expande/recolhe um bloco com deslize suave nos DOIS sentidos (grid-template-rows
 * 0fr <-> 1fr, sem número mágico) + fade.
 *
 * Diferente do <Revelar>, que só anima na montagem (`{cond && <Revelar>}`) e some
 * seco ao desmontar. Aqui o conteúdo fica sempre montado; passe `aberto` e ele
 * anima abrir E fechar. Use quando um toggle liga/desliga uma seção que precisa
 * sair tão suave quanto entrou (ex.: "Todos" <-> "Faixa específica").
 */
export function Colapsavel({
  aberto,
  children,
  className,
}: {
  aberto: boolean
  children: React.ReactNode
  className?: string
}) {
  return (
    <div className={clsx(styles.wrap, !aberto && styles.fechado)} aria-hidden={!aberto} inert={!aberto}>
      <div className={clsx(styles.inner, className)}>{children}</div>
    </div>
  )
}
