'use client'

import { useEffect, useState } from 'react'
import { X, Check } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useAtualizarComentario } from '@/hooks/postagem/useAtualizarComentario'
import { notificar } from '@/components/common/Notificacao/notificar'
import styles from './ModalEditarComentario.module.css'

interface Props {
  postagemId: string
  comentarioId: string
  conteudoInicial: string
  aoFechar: () => void
}

export function ModalEditarComentario({
  postagemId,
  comentarioId,
  conteudoInicial,
  aoFechar,
}: Props) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 200)
  const atualizarComentario = useAtualizarComentario()
  const [conteudo, setConteudo] = useState(conteudoInicial)

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !atualizarComentario.isPending) fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar, atualizarComentario.isPending])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!conteudo.trim() || atualizarComentario.isPending) return

    atualizarComentario.mutate(
      { postagemId, comentarioId, conteudo: conteudo.trim() },
      {
        onSuccess: () => {
          notificar.sucesso('Comentário atualizado', 'O comentário foi alterado com sucesso.')
          fechar()
        },
        onError: () => {
          notificar.erro('Não foi possível salvar', 'Tente atualizar o comentário novamente.')
        },
      },
    )
  }

  return (
    <div
      className={clsx(styles.overlay, saindo && styles.saindo)}
      onMouseDown={() => !atualizarComentario.isPending && fechar()}
    >
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo}>Editar Comentário</h2>
          <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.modalCorpo}>
            <input
              type="text"
              className={styles.inputComentario}
              value={conteudo}
              onChange={(e) => setConteudo(e.target.value)}
              autoFocus
              placeholder="Digite o comentário..."
            />
          </div>

          <div className={styles.modalRodape}>
            <button
              type="button"
              className={styles.btnCancelar}
              onClick={fechar}
              disabled={atualizarComentario.isPending}
            >
              Cancelar
            </button>
            <button
              type="submit"
              className={styles.btnSalvar}
              disabled={atualizarComentario.isPending || !conteudo.trim()}
            >
              <Check size={14} />
              {atualizarComentario.isPending ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
