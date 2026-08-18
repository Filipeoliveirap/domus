'use client'

import { ModalConfirmacaoCritica, type Consequencia } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarCategoria } from '@/hooks/financeiro/categoria/useArquivarCategoria'
import { useContagemMovimentacoesCategoria } from '@/hooks/financeiro/categoria/useContagemMovimentacoesCategoria'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'

/**
 * Confirmação por escrito: arquivar uma categoria some com ela do relatório "por categoria"
 * e do "maior lançamento" na hora — mesmo as movimentações que já a usam. Reversível (a
 * categoria e os relatórios voltam ao normal ao restaurar), mas o efeito imediato é real.
 */
export function ModalArquivarCategoria({
  categoria,
  onClose,
}: {
  categoria: CategoriaResponse
  onClose: () => void
}) {
  const { confirmar, isLoading, erroGeral } = useArquivarCategoria(categoria, onClose)
  const { data: total, isLoading: contando } = useContagemMovimentacoesCategoria(categoria.id)

  const consequencias: Consequencia[] = [
    {
      tipo: 'perde',
      texto: contando
        ? 'Some do relatório "por categoria" e do "maior lançamento" enquanto estiver arquivada'
        : total
          ? `Some do relatório "por categoria" e do "maior lançamento" enquanto estiver arquivada — hoje ${total} ${total === 1 ? 'movimentação usa' : 'movimentações usam'} esta categoria`
          : 'Some do relatório "por categoria" e do "maior lançamento" enquanto estiver arquivada',
    },
    { tipo: 'mantem', texto: 'As movimentações que já usam esta categoria continuam existindo e aparecem nas listagens e nos totais gerais normalmente' },
    { tipo: 'mantem', texto: 'É reversível: ao restaurar a categoria, os relatórios voltam a mostrá-la' },
  ]

  return (
    <ModalConfirmacaoCritica
      titulo="Arquivar categoria?"
      mensagem={
        <>
          Ao arquivar <strong>{categoria.nome}</strong>, ela deixa de aparecer no cadastro de
          novas movimentações e some dos relatórios enquanto estiver arquivada.
        </>
      }
      consequencias={consequencias}
      palavraConfirmacao={categoria.nome}
      textoConfirmar="Arquivar categoria"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
