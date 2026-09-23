'use client'

import { useEffect, useState } from 'react'
import { X } from 'lucide-react'
import { clsx } from 'clsx'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { useCriarPostagem } from '@/hooks/postagem/useCriarPostagem'
import { Select } from '@/components/common/select/Select'
import type { TipoPostagem } from '@/types/postagem.type'
import styles from './ModalNovoAviso.module.css'

const OPCOES_TIPO = [
  { value: 'MURAL_AVISO', label: 'Comunicado Pastoral / Aviso Geral' },
  { value: 'RESUMO_CULTO', label: 'Escala de Voluntários' },
  { value: 'GERAL', label: 'Ação Social / Geral' },
]

export function ModalNovoAviso({ aoFechar }: { aoFechar: () => void }) {
  const { saindo, fechar } = useFecharAnimado(aoFechar, 220)
  const criarPostagem = useCriarPostagem()
  const [titulo, setTitulo] = useState('')
  const [conteudo, setConteudo] = useState('')
  const [tipo, setTipo] = useState<TipoPostagem>('MURAL_AVISO')

  useEffect(() => {
    const aoTeclar = (e: KeyboardEvent) => {
      if (e.key === 'Escape') fechar()
    }
    document.addEventListener('keydown', aoTeclar)
    return () => document.removeEventListener('keydown', aoTeclar)
  }, [fechar])

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!conteudo.trim()) return

    criarPostagem.mutate(
      {
        tipo,
        oficial: true,
        titulo: titulo.trim() || undefined,
        conteudo: conteudo.trim(),
        fixado: false,
      },
      {
        onSuccess: () => fechar(),
      },
    )
  }

  return (
    <div className={clsx(styles.overlay, saindo && styles.saindo)} onMouseDown={fechar}>
      <div
        className={styles.modal}
        onMouseDown={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
      >
        <span className={styles.grabber} aria-hidden="true" />
        <div className={styles.modalHeader}>
          <h2 className={styles.modalTitulo}>Publicar Aviso no Mural</h2>
          <button type="button" className={styles.modalFechar} onClick={fechar} aria-label="Fechar">
            <X size={18} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <div className={styles.modalCorpo}>
            <Select
              id="tipo-aviso"
              label="Tipo do Aviso"
              value={tipo}
              options={OPCOES_TIPO}
              onChange={(e) => setTipo(e.target.value as TipoPostagem)}
            />

            <div className={styles.campo}>
              <label className={styles.label} htmlFor="titulo-aviso">
                Título do Aviso
              </label>
              <input
                id="titulo-aviso"
                type="text"
                className={styles.input}
                placeholder="Ex: Culto de Celebração & Santa Ceia"
                value={titulo}
                onChange={(e) => setTitulo(e.target.value)}
              />
            </div>

            <div className={styles.campo}>
              <label className={styles.label} htmlFor="conteudo-aviso">
                Conteúdo do Comunicado
              </label>
              <textarea
                id="conteudo-aviso"
                className={styles.textarea}
                placeholder="Digite as informações importantes do aviso..."
                value={conteudo}
                onChange={(e) => setConteudo(e.target.value)}
                required
              />
            </div>
          </div>

          <div className={styles.modalRodape}>
            <button type="button" className={styles.btnCancelar} onClick={fechar}>
              Cancelar
            </button>
            <button
              type="submit"
              className={styles.btnSalvar}
              disabled={criarPostagem.isPending || !conteudo.trim()}
            >
              {criarPostagem.isPending ? 'Publicando...' : 'Publicar no Mural'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
