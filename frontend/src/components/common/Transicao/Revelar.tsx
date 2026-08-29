'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import styles from './Revelar.module.css'

/**
 * Revela um bloco que aparece condicionalmente (ex.: campos que surgem ao ligar um toggle)
 * com uma expansão de altura suave + fade — em vez de "pipocar" o layout e só depois
 * aparecer.
 *
 * Usa o truque de `grid-template-rows: 0fr -> 1fr` (anima altura sem número mágico) com
 * `@starting-style`, então só dispara na MONTAGEM, não a cada render. Para o padrão
 * `{condicao && <Revelar>...</Revelar>}`.
 *
 * O `overflow: hidden` (necessário pra altura colapsar) é solto quando a animação termina —
 * senão um dropdown/tooltip dentro do bloco ficaria recortado.
 *
 * `className` vai no elemento interno (o que de fato contém os filhos) — passe a classe de
 * layout do bloco (ex.: a que dá `display:flex; gap`) ali.
 */
export function Revelar({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  const [animando, setAnimando] = useState(true)

  // Rede de segurança: se o transitionend não vier (sem transição, browser antigo), libera
  // o overflow mesmo assim.
  useEffect(() => {
    const t = setTimeout(() => setAnimando(false), 500)
    return () => clearTimeout(t)
  }, [])

  return (
    <div
      className={styles.wrap}
      onTransitionEnd={(e) => {
        if (e.propertyName === 'grid-template-rows') setAnimando(false)
      }}
    >
      <div className={clsx(styles.inner, !animando && styles.pronto, className)}>{children}</div>
    </div>
  )
}
