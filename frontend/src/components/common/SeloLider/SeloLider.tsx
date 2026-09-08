'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import { Crown, Star } from 'lucide-react'
import styles from './SeloLider.module.css'

/**
 * Mantém o elemento montado por `ms` depois que `ativo` vira false, pra dar tempo da
 * animação de saída rodar — assim tornar/remover liderança fica suave nos dois sentidos,
 * não só na entrada. Ajuste de estado em render (troca de prop), sem setState em effect.
 */
function useMontagemAnimada(ativo: boolean, ms: number) {
  const [render, setRender] = useState(ativo)
  const [anterior, setAnterior] = useState(ativo)
  const [saindo, setSaindo] = useState(false)

  if (ativo !== anterior) {
    setAnterior(ativo)
    if (ativo) {
      setRender(true)
      setSaindo(false)
    } else {
      setSaindo(true)
    }
  }

  useEffect(() => {
    if (!saindo) return
    const t = window.setTimeout(() => {
      setRender(false)
      setSaindo(false)
    }, ms)
    return () => window.clearTimeout(t)
  }, [saindo, ms])

  return { render, saindo }
}

/** Pílula "Líder" (coroa + texto), com animação de entrada e de saída. */
export function SeloLider({ ativo }: { ativo: boolean }) {
  const { render, saindo } = useMontagemAnimada(ativo, 240)
  if (!render) return null
  return (
    <span className={clsx(styles.selo, saindo && styles.saindo)}>
      <Crown size={12} aria-hidden="true" /> Líder
    </span>
  )
}

/** Estrelinha ao lado do nome, com a mesma animação de entrada/saída do selo. */
export function EstrelaLider({ ativo }: { ativo: boolean }) {
  const { render, saindo } = useMontagemAnimada(ativo, 240)
  if (!render) return null
  return <Star size={14} className={clsx(styles.estrela, saindo && styles.saindo)} aria-hidden="true" />
}
