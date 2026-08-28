'use client'

import { useEffect, useState } from 'react'
import { clsx } from 'clsx'
import styles from './NavProgress.module.css'

type Fase = 'oculto' | 'carregando' | 'finalizando'

export function BarraProgresso({ ativo }: { ativo: boolean }) {
  const [fase, setFase] = useState<Fase>('oculto')

  useEffect(() => {
    if (ativo) {
      const id = setTimeout(() => setFase('carregando'), 0)
      return () => clearTimeout(id)
    }
    // ativo virou false: completa a barra (finalizando) e depois some (oculto).
    // Updater funcional pra não animar quando já estava oculto (ex.: no mount).
    const idFim = setTimeout(() => setFase((f) => (f === 'oculto' ? f : 'finalizando')), 0)
    const idOcultar = setTimeout(() => setFase('oculto'), 320)
    return () => {
      clearTimeout(idFim)
      clearTimeout(idOcultar)
    }
  }, [ativo])

  return (
    <div
      className={clsx(
        styles.barra,
        fase !== 'oculto' && styles.barraVisivel,
        fase === 'carregando' && styles.faseCarregando,
        fase === 'finalizando' && styles.faseFinalizando,
      )}
      aria-hidden="true"
    >
      <div className={styles.barraInterna} />
    </div>
  )
}
