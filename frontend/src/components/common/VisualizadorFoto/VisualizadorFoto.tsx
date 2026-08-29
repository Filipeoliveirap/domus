'use client'

import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { urlFoto } from '@/lib/urlFoto'
import styles from './VisualizadorFoto.module.css'

interface Props {
  fotoId: string
  descricao: string
  onClose: () => void
}

export function VisualizadorFoto({ fotoId, descricao, onClose }: Props) {
  const fecharRef = useRef<HTMLButtonElement>(null)
  const { saindo, fechar } = useFecharAnimado(onClose, 200)

  useEffect(() => {
    fecharRef.current?.focus()
  }, [])

  useEffect(() => {
    const anterior = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => { document.body.style.overflow = anterior }
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        // O drawer por baixo também escuta Escape. Sem isto, um toque fecharia os dois
        // de uma vez, e a pessoa perderia o cadastro que estava lendo.
        e.stopPropagation()
        fechar()
      }
    }
    document.addEventListener('keydown', aoTeclar, true)
    return () => document.removeEventListener('keydown', aoTeclar, true)
  }, [fechar])

  return (
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      onMouseDown={fechar}
      role="dialog"
      aria-modal="true"
      aria-label={descricao}
    >
      <button
        ref={fecharRef}
        type="button"
        className={styles.fechar}
        onClick={fechar}
        onMouseDown={(e) => e.stopPropagation()}
        aria-label="Fechar visualização"
      >
        <X size={22} aria-hidden="true" />
      </button>

      {/* eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos */}
      <img
        src={urlFoto(fotoId, 'DISPLAY')!}
        alt={descricao}
        className={styles.imagem}
        onMouseDown={(e) => e.stopPropagation()}
      />
    </div>
  )
}
