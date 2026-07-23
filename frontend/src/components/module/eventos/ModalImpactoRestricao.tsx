'use client'

import { useEffect } from 'react'
import { X, AlertTriangle } from 'lucide-react'
import { Button } from '@/components/common/button/Button'
import type { InscritoImpactado } from '@/types/evento.type'
import styles from './ModalImpactoRestricao.module.css'

interface Props {
  afetados: InscritoImpactado[]
  isLoading: boolean
  onManterTodos: () => void
  onCancelarNaoElegiveis: () => void
  onClose: () => void
}

/**
 * Aviso de impacto retroativo (Task 6/9): abre quando apertar/ligar uma restrição de
 * elegibilidade deixaria alguém já inscrito de fora. Nunca cancela sozinho — o admin
 * decide entre manter todo mundo (a restrição vale só para novas inscrições) ou cancelar
 * quem não é mais elegível. Sem `window.confirm`: segue o padrão de modal do projeto.
 */
export function ModalImpactoRestricao({
  afetados, isLoading, onManterTodos, onCancelarNaoElegiveis, onClose,
}: Props) {
  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !isLoading) onClose()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [onClose, isLoading])

  return (
    <div className={styles.overlay} onMouseDown={() => !isLoading && onClose()}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        aria-labelledby="titulo-impacto-restricao"
      >
        <div className={styles.header}>
          <div className={styles.headerTexto}>
            <span className={styles.iconBox}>
              <AlertTriangle size={20} aria-hidden="true" />
            </span>
            <div>
              <h2 className={styles.titulo} id="titulo-impacto-restricao">
                {afetados.length === 1
                  ? '1 pessoa inscrita deixaria de ser elegível'
                  : `${afetados.length} pessoas inscritas deixariam de ser elegíveis`}
              </h2>
              <p className={styles.subtitulo}>
                Essas restrições valeriam para quem já está inscrito. Decida o que fazer
                com quem não se encaixa mais.
              </p>
            </div>
          </div>
          <button
            type="button"
            className={styles.btnFechar}
            onClick={onClose}
            aria-label="Fechar"
            disabled={isLoading}
          >
            <X size={20} />
          </button>
        </div>

        <ul className={styles.lista}>
          {afetados.map((pessoa) => (
            <li key={pessoa.pessoaId} className={styles.item}>
              <span className={styles.itemNome}>{pessoa.nome}</span>
              <span className={styles.itemMotivo}>{pessoa.motivos.join(' · ')}</span>
            </li>
          ))}
        </ul>

        <div className={styles.footer}>
          <Button
            type="button"
            variant="danger"
            size="md"
            isLoading={isLoading}
            onClick={onCancelarNaoElegiveis}
          >
            Cancelar os {afetados.length}
          </Button>
          <Button
            type="button"
            variant="primary"
            size="md"
            isLoading={isLoading}
            onClick={onManterTodos}
          >
            Manter todos
          </Button>
        </div>
      </div>
    </div>
  )
}
