'use client'

import { useState } from 'react'
import { useCriarMinisterio, useAtualizarMinisterio } from '@/hooks/ministerio/useMinisterioForm'
import { ROTULO_MINISTERIO } from '@/lib/rotulosMinisterio'
import { Input } from '@/components/common/input/Input'
import { Button } from '@/components/common/button/Button'
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
  const [erro, setErro] = useState<string | undefined>(undefined)
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
        <Input
          id="nome-ministerio"
          label="Nome"
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          error={erro}
          placeholder="Ex.: Louvor"
          autoFocus
        />
        <div className={styles.acoes}>
          <Button type="button" variant="secondary" onClick={onClose} disabled={salvando}>
            Cancelar
          </Button>
          <Button type="button" variant="primary" disabled={!nome.trim() || salvando} isLoading={salvando} loadingText="Salvando…" onClick={salvar}>
            Salvar
          </Button>
        </div>
      </div>
    </div>
  )
}
