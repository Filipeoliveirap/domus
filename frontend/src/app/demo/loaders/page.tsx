'use client'

import { useState } from 'react'
import { notFound } from 'next/navigation'
import { Loader, type LoaderVariant } from '@/components/common/Loader/Loader'
import styles from './page.module.css'

const VARIANTES: LoaderVariant[] = [
  'circular', 'classic', 'pulse', 'pulse-dot', 'dots', 'typing',
  'wave', 'bars', 'terminal', 'text-blink', 'text-shimmer', 'loading-dots',
]

const TAMANHOS = ['sm', 'md', 'lg'] as const

export default function DemoLoadersPage() {
  if (process.env.NODE_ENV === 'production') notFound()

  const [tamanho, setTamanho] = useState<(typeof TAMANHOS)[number]>('md')

  return (
    <div className={styles.wrapper}>
      <h1 className={styles.titulo}>Loaders</h1>
      <p className={styles.aviso}>Rota de teste (dev-only). Escolha quais variantes ficam.</p>

      <div className={styles.toggle}>
        {TAMANHOS.map((t) => (
          <button key={t} data-ativo={tamanho === t} onClick={() => setTamanho(t)}>
            {t}
          </button>
        ))}
      </div>

      <div className={styles.grid}>
        {VARIANTES.map((v) => (
          <div key={v} className={styles.celula}>
            <Loader variant={v} size={tamanho} text="Carregando" />
            <span className={styles.nome}>{v}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
