'use client'

import { useState } from 'react'
import { useCriarMinisterio, useAtualizarMinisterio } from '@/hooks/ministerio/useMinisterioForm'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
import type { MinisterioResponse } from '@/types/ministerio.type'
import styles from './ModalMinisterioForm.module.css'

interface Props {
  ministerio: MinisterioResponse | null
  onClose: () => void
}

// As mutations (useCriarMinisterio/useAtualizarMinisterio) já disparam notificar.sucesso/erro
// sozinhas (ver Task 9) — este componente só decide fechar o modal em caso de sucesso.
export function ModalMinisterioForm({ ministerio, onClose }: Props) {
  const [nome, setNome] = useState(ministerio?.nome ?? '')
  const criar = useCriarMinisterio()
  const atualizar = useAtualizarMinisterio(ministerio?.id ?? '')
  const salvando = criar.isPending || atualizar.isPending

  async function salvar() {
    try {
      if (ministerio) {
        await atualizar.mutateAsync({ nome })
      } else {
        await criar.mutateAsync({ nome })
      }
      onClose()
    } catch {
      // erro já notificado pela mutation; modal fica aberto para o usuário tentar de novo.
    }
  }

  return (
    <div className={styles.overlay} onMouseDown={onClose}>
      <div className={styles.modal} onMouseDown={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 className={styles.titulo}>
          {ministerio ? `Editar ${ROTULO_MINISTERIO.toLowerCase()}` : `Nova ${ROTULO_MINISTERIO.toLowerCase()}`}
        </h2>
        <label className={styles.label} htmlFor="nome-ministerio">Nome</label>
        <input
          id="nome-ministerio"
          className={styles.input}
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          placeholder="Ex.: Louvor"
          autoFocus
        />
        <div className={styles.acoes}>
          <button type="button" className={styles.botaoSecundario} onClick={onClose} disabled={salvando}>
            Cancelar
          </button>
          <button type="button" className={styles.botaoPrimario} disabled={!nome.trim() || salvando} onClick={salvar}>
            {salvando ? 'Salvando…' : 'Salvar'}
          </button>
        </div>
      </div>
    </div>
  )
}
