'use client'

import { ModalArquivar } from '@/components/common/modalArquivar/ModalArquivar'
import { useArquivarMovimentacao } from '@/hooks/financeiro/movimentacao/useArquivarMovimentacao'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { MovimentacaoResponse } from '@/types/financeiro/movimentacao.type'

export function ModalArquivarMovimentacao({
  movimentacao,
  onClose,
}: {
  movimentacao: MovimentacaoResponse
  onClose: () => void
}) {
  const { confirmar, isLoading, erroGeral } = useArquivarMovimentacao(movimentacao, onClose)

  return (
    <ModalArquivar
      titulo="Arquivar movimentação?"
      mensagem={
        <>
          Ao arquivar esta movimentação de <strong>{formatarMoeda(movimentacao.valor)}</strong>
          {' '}({movimentacao.categoriaNome}), ela deixará de aparecer nas listagens e nos relatórios.
        </>
      }
      aviso="Os registros históricos são preservados e a movimentação pode ser restaurada por um administrador."
      onConfirmar={confirmar}
      onClose={onClose}
      isLoading={isLoading}
      erro={erroGeral}
    />
  )
}