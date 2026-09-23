'use client'

import { useEffect } from 'react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { versiculoDoDia } from '@/lib/versiculos'
import styles from './inicioMobile.module.css'

export function DrawerVersiculoMobile({ aoFechar }: { aoFechar: () => void }) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const versiculo = versiculoDoDia()

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = anterior
    }
  }, [])

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div className={styles.drawer} onMouseDown={(e) => e.stopPropagation()}>
        <div className={styles.grabber} aria-hidden="true" />
        <p className={styles.versiculoCorpo}>&ldquo;{versiculo.texto}&rdquo;</p>
        <span className={styles.versiculoRef}>— {versiculo.ref}</span>
      </div>
    </div>
  )
}
