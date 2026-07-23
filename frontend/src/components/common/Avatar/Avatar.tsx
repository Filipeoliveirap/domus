'use client'

import { useEffect, useState } from 'react'
import styles from './Avatar.module.css'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'

interface AvatarProps {
  fotoId: string | null | undefined
  nome: string
  tamanho?: 'sm' | 'md' | 'lg'
}

export function Avatar({ fotoId, nome, tamanho = 'md' }: AvatarProps) {
  const [erro, setErro] = useState(false)
  const url = urlFoto(fotoId, tamanho === 'lg' ? 'DISPLAY' : 'THUMB')

  // Se a foto trocar (ex.: usuário atualizou a própria em Meu Perfil), dá uma nova chance
  // à imagem nova antes de assumir que também vai falhar.
  useEffect(() => {
    setErro(false)
  }, [url])

  return url && !erro ? (
    <img
      src={url}
      alt={nome}
      className={`${styles.avatar} ${styles[tamanho]}`}
      onError={() => setErro(true)}
    />
  ) : (
    <span className={`${styles.avatar} ${styles.iniciais} ${styles[tamanho]}`}>
      {iniciais(nome)}
    </span>
  )
}
