import styles from './Avatar.module.css'
import { urlFoto } from '@/lib/urlFoto'
import { iniciais } from '@/lib/formats/pessoaFormat'

interface AvatarProps {
  fotoId: string | null | undefined
  nome: string
  tamanho?: 'sm' | 'md' | 'lg'
}

export function Avatar({ fotoId, nome, tamanho = 'md' }: AvatarProps) {
  const url = urlFoto(fotoId, tamanho === 'lg' ? 'DISPLAY' : 'THUMB')

  return url ? (
    <img src={url} alt={nome} className={`${styles.avatar} ${styles[tamanho]}`} />
  ) : (
    <span className={`${styles.avatar} ${styles.iniciais} ${styles[tamanho]}`}>
      {iniciais(nome)}
    </span>
  )
}
