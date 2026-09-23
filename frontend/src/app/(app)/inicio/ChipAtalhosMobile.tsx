'use client'

import { useState } from 'react'
import { Quote, Cake } from 'lucide-react'
import { DrawerVersiculoMobile } from './DrawerVersiculoMobile'
import styles from './inicioMobile.module.css'

export function ChipAtalhosMobile({
  totalAniversariantes,
  onAbrirAniversariantes,
}: {
  totalAniversariantes: number
  onAbrirAniversariantes: () => void
}) {
  const [drawerVersiculoAberto, setDrawerVersiculoAberto] = useState(false)

  return (
    <>
      <div className={styles.barraChips}>
        <button
          type="button"
          className={styles.chip}
          onClick={() => setDrawerVersiculoAberto(true)}
        >
          <Quote size={14} />
          Versículo do dia
        </button>

        <button
          type="button"
          className={styles.chip}
          onClick={onAbrirAniversariantes}
        >
          <Cake size={14} />
          Aniversariantes ({totalAniversariantes})
        </button>
      </div>

      {drawerVersiculoAberto && (
        <DrawerVersiculoMobile aoFechar={() => setDrawerVersiculoAberto(false)} />
      )}
    </>
  )
}
