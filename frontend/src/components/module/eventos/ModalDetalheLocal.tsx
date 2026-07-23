'use client'

import { useEffect } from 'react'
import { X, MapPin, Building2 } from 'lucide-react'
import type { EventoLocalInfo } from '@/types/evento.type'
import styles from './ModalDetalheLocal.module.css'

interface Props {
  local: EventoLocalInfo
  onClose: () => void
}

/**
 * Detalhe de um local CADASTRADO do evento — nome e endereço completo. Só faz sentido para
 * local com `id` (cadastro navegável): o ad-hoc de texto livre não tem endereço a mostrar,
 * então quem abre este modal já filtrou por `local.id`.
 *
 * <p>Quando o local herda o endereço da igreja (`enderecoHerdado`), mostra isso
 * explicitamente — a pessoa entende que o endereço é o da sede, não um cadastrado à parte.
 */
export function ModalDetalheLocal({ local, onClose }: Props) {
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose])

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="detalhe-local-titulo"
      >
        <button type="button" className={styles.fechar} onClick={onClose} aria-label="Fechar">
          <X size={20} />
        </button>

        <div className={styles.cabecalho}>
          <span className={styles.icone}><MapPin size={22} aria-hidden="true" /></span>
          <h2 className={styles.titulo} id="detalhe-local-titulo">{local.nome}</h2>
        </div>

        <div className={styles.corpo}>
          {local.endereco ? (
            <p className={styles.endereco}>{local.endereco}</p>
          ) : (
            <p className={styles.semEndereco}>Este local ainda não tem endereço cadastrado.</p>
          )}

          {local.enderecoHerdado && local.endereco && (
            <p className={styles.herdado}>
              <Building2 size={14} aria-hidden="true" />
              Endereço da igreja — este local não tem um próprio.
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
