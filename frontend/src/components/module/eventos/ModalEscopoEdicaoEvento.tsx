'use client'

import { useEffect, useRef } from 'react'
import { Repeat } from 'lucide-react'
import baseStyles from '@/components/common/ModalConfirmacao/ModalConfirmacao.module.css'
import styles from './ModalEscopoEdicaoEvento.module.css'
import type { EscopoEdicaoEvento } from '@/types/evento.type'

interface Props {
  titulo: string
  onEscolher: (escopo: EscopoEdicaoEvento) => void
  onClose: () => void
}

/** Esta ocorrência faz parte de uma série recorrente — pergunta o alcance antes de
 *  editar/arquivar, igual qualquer calendário maduro (Google Calendar) já resolve. */
export function ModalEscopoEdicaoEvento({ titulo, onEscolher, onClose }: Props) {
  const primeiraRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    primeiraRef.current?.focus()
  }, [])

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  return (
    <div className={baseStyles.overlay} onMouseDown={() => onClose()}>
      <div
        className={baseStyles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-escopo-titulo"
      >
        <div className={baseStyles.cabecalho}>
          <span className={baseStyles.iconBox}>
            <Repeat size={22} aria-hidden="true" />
          </span>
          <h2 className={baseStyles.titulo} id="modal-escopo-titulo">
            &quot;{titulo}&quot; faz parte de uma série
          </h2>
        </div>

        <div className={baseStyles.corpo}>
          <p>O que você quer alterar?</p>
        </div>

        <div className={styles.opcoes}>
          <button ref={primeiraRef} type="button" className={styles.opcao} onClick={() => onEscolher('ESTA')}>
            Só este
          </button>
          <button type="button" className={styles.opcao} onClick={() => onEscolher('ESTA_E_SEGUINTES')}>
            Este e os seguintes
          </button>
          <button type="button" className={styles.opcao} onClick={() => onEscolher('SERIE')}>
            Toda a série
          </button>
        </div>

        <div className={baseStyles.rodape}>
          <button type="button" className={baseStyles.btnCancelar} onClick={onClose}>
            Cancelar
          </button>
        </div>
      </div>
    </div>
  )
}
