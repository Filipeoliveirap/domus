'use client'

import { useEffect, useRef } from 'react'
import { X } from 'lucide-react'
import { urlFoto } from '@/lib/urlFoto'
import styles from './VisualizadorFoto.module.css'

interface Props {
  /** Id da foto. O componente monta a URL sozinho — quem chama não precisa saber o tamanho. */
  fotoId: string
  /** Descreve a imagem para leitor de tela. Ex.: "Foto de João da Silva". */
  descricao: string
  onClose: () => void
}

/**
 * Abre a foto em tamanho grande, por cima do drawer que a mostrou.
 *
 * <p>Sempre pede a versão <b>DISPLAY</b> (1200px): é a maior que o Domus serve. O
 * `original` fica guardado mas nunca é servido — ele existe para poder gerar tamanhos
 * novos no futuro, não para ir ao navegador de ninguém.
 *
 * <p>Fica em `z-index` acima dos drawers (que estão em 100) porque abre a partir de um
 * deles: nascer abaixo de quem o abriu o deixaria invisível.
 */
export function VisualizadorFoto({ fotoId, descricao, onClose }: Props) {
  const fecharRef = useRef<HTMLButtonElement>(null)

  // O foco vai para o botão de fechar: quem navega por teclado precisa de uma saída
  // alcançável sem passear por trás do overlay.
  useEffect(() => {
    fecharRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        // O drawer por baixo também escuta Escape. Sem isto, um toque fecharia os dois
        // de uma vez, e a pessoa perderia o cadastro que estava lendo.
        e.stopPropagation()
        onClose()
      }
    }
    document.addEventListener('keydown', aoTeclar, true)
    return () => document.removeEventListener('keydown', aoTeclar, true)
  }, [onClose])

  return (
    <div
      className={styles.overlay}
      onMouseDown={onClose}
      role="dialog"
      aria-modal="true"
      aria-label={descricao}
    >
      <button
        ref={fecharRef}
        type="button"
        className={styles.fechar}
        onClick={onClose}
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
