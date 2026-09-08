'use client'

import { useState, type CSSProperties } from 'react'
import styles from './Avatar.module.css'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'

interface AvatarProps {
  fotoId: string | null | undefined
  nome: string
  /** `'sm'|'md'|'lg'` usam os tamanhos padrão; um número dá px exatos (ex.: 40). */
  tamanho?: 'sm' | 'md' | 'lg' | number
  /** Passado = a foto vira clicável (abre em tamanho grande via VisualizadorFoto) e ganha
   *  o feedback de "dá pra clicar": leve zoom no hover, encolhida no toque. Sem foto real
   *  (só iniciais), fica sem interação. */
  onVerFoto?: () => void
}

export function Avatar({ fotoId, nome, tamanho = 'md', onVerFoto }: AvatarProps) {
  const [erro, setErro] = useState(false)
  const grande = tamanho === 'lg' || (typeof tamanho === 'number' && tamanho >= 72)
  const url = urlFoto(fotoId, grande ? 'DISPLAY' : 'THUMB')

  // Se a foto trocar (ex.: pessoa atualizou a própria), dá nova chance à imagem antes de
  // assumir que também vai falhar — ajuste de estado no render (padrão React).
  const [urlAnterior, setUrlAnterior] = useState(url)
  if (url !== urlAnterior) {
    setUrlAnterior(url)
    setErro(false)
  }

  const temFoto = !!url && !erro
  const classeTamanho = typeof tamanho === 'number' ? '' : styles[tamanho]
  const style: CSSProperties | undefined =
    typeof tamanho === 'number'
      ? { width: tamanho, height: tamanho, fontSize: Math.round(tamanho * 0.34) }
      : undefined

  const conteudo = temFoto ? (
    // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
    <img src={url} alt={nome} className={styles.foto} onError={() => setErro(true)} />
  ) : (
    <span className={styles.iniciaisTexto}>{iniciais(nome)}</span>
  )

  if (temFoto && onVerFoto) {
    return (
      <button
        type="button"
        className={`${styles.avatar} ${styles.clicavel} ${classeTamanho}`}
        style={style}
        onClick={(e) => { e.stopPropagation(); onVerFoto() }}
        aria-label={`Ver foto de ${nome}`}
      >
        {conteudo}
      </button>
    )
  }

  return (
    <span
      className={`${styles.avatar} ${temFoto ? '' : styles.iniciais} ${classeTamanho}`}
      style={style}
    >
      {conteudo}
    </span>
  )
}
