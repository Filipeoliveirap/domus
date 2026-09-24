'use client'

import { useEffect } from 'react'
import { Trash2 } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import styles from './ModalConfirmacaoExclusao.module.css'

interface Props {
  titulo: string
  mensagem: string
  carregando?: boolean
  onConfirmar: () => void
  onClose: () => void
}

export function ModalConfirmacaoExclusao({
  titulo,
  mensagem,
  carregando = false,
  onConfirmar,
  onClose,
}: Props) {
  const { saindo, fechar } = useFecharAnimado(onClose, 200)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !carregando) fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, carregando])

  return (
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      onMouseDown={() => !carregando && fechar()}
    >
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className={styles.cabecalho}>
          <div className={styles.iconeAviso}>
            <Trash2 size={20} />
          </div>
          <h2 className={styles.titulo}>{titulo}</h2>
        </div>

        <div className={styles.corpo}>
          <p>{mensagem}</p>
        </div>

        <div className={styles.rodape}>
          <button
            type="button"
            className={styles.btnCancelar}
            onClick={fechar}
            disabled={carregando}
          >
            Cancelar
          </button>
          <button
            type="button"
            className={styles.btnExcluir}
            onClick={onConfirmar}
            disabled={carregando}
          >
            {carregando ? 'Excluindo...' : 'Excluir'}
          </button>
        </div>
      </div>
    </div>
  )
}
