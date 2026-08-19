'use client'

import { ModalConfirmacaoCritica, type Consequencia } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { useArquivarMovimentacao } from '@/hooks/financeiro/movimentacao/useArquivarMovimentacao'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { MovimentacaoResponse } from '@/types/financeiro/movimentacao.type'

/**
 * Confirmação por escrito: arquivar uma movimentação some com ela das listagens e de TODOS
 * os totais de relatório (resumo, evolução mensal, por categoria, maior lançamento) na hora
 * — não é só "vai pra outra aba". Reversível (volta tudo ao restaurar), mas o efeito
 * imediato é real.
 */
export function ModalArquivarMovimentacao({
  movimentacao,
  onClose,
}: {
  movimentacao: MovimentacaoResponse
  onClose: () => void
}) {
  const { confirmar, isLoading, erroGeral } = useArquivarMovimentacao(movimentacao, onClose)

  const identificacao = `${formatarMoeda(movimentacao.valor)} (${movimentacao.categoriaNome})`

  const temContribuinte = movimentacao.contribuintes.length > 0

  const consequencias: Consequencia[] = [
    { tipo: 'perde', texto: 'Some das listagens e de todos os totais de relatório (resumo, evolução mensal, por categoria, maior lançamento) enquanto estiver arquivada' },
  ]
  if (temContribuinte) {
    consequencias.push({ tipo: 'mantem', texto: 'O vínculo dos contribuintes com esta movimentação continua existindo' })
  }
  consequencias.push({ tipo: 'mantem', texto: 'É reversível: ao restaurar, volta a aparecer nas listagens e nos relatórios' })

  return (
    <ModalConfirmacaoCritica
      titulo="Arquivar movimentação?"
      mensagem={
        <>
          Ao arquivar esta movimentação de <strong>{identificacao}</strong>, ela deixa de
          aparecer nas listagens e nos relatórios.
        </>
      }
      consequencias={consequencias}
      palavraConfirmacao="confirmar"
      textoConfirmar="Arquivar movimentação"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
