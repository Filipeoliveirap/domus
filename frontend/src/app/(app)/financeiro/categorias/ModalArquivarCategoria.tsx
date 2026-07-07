'use client'

import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { useArquivarCategoria } from '@/hooks/financeiro/categoria/useArquivarCategoria'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'

export function ModalArquivarCategoria({
  categoria,
  onClose,
}: {
  categoria: CategoriaResponse
  onClose: () => void
}) {
  const { confirmar, isLoading, erroGeral } = useArquivarCategoria(categoria, onClose)

  return (
    <ModalArquivar
      titulo="Arquivar categoria?"
      mensagem={
        <>
          Ao arquivar <strong>{categoria.nome}</strong>, ela deixará de aparecer no cadastro de
          novas movimentações. As movimentações que já a usam permanecem intactas.
        </>
      }
      onConfirmar={confirmar}
      onClose={onClose}
      isLoading={isLoading}
      erro={erroGeral}
    />
  )
}