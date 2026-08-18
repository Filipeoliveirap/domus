'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { useMovimentacoesArquivadas } from '@/hooks/financeiro/movimentacao/useMovimentacoesArquivadas'
import { useRestaurarMovimentacao } from '@/hooks/financeiro/movimentacao/useRestaurarMovimentacao'
import { useExcluirMovimentacaoDefinitivamente } from '@/hooks/financeiro/movimentacao/useExcluirMovimentacaoDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { ModalConfirmacaoCritica, type Consequencia } from '@/components/common/ModalConfirmacaoCritica/ModalConfirmacaoCritica'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { formatarMoeda, formatarData, rotuloTipo, varianteTipo } from '@/lib/formats/financeiro/movimentacaoFormat'
import { DrawerDetalheMovimentacao } from '@/app/(app)/financeiro/movimentacoes/(lista)/(detalhe)/DrawerDetalheMovimentacao'
import type { MovimentacaoArquivadaResponse } from '@/types/financeiro/movimentacao.type'
import styles from './arquivadas.module.css'

function IconeTipo({ tipo }: { tipo: MovimentacaoArquivadaResponse['tipo'] }) {
  return tipo === 'ENTRADA' ? <ArrowDownCircle size={18} /> : <ArrowUpCircle size={18} />
}

export default function MovimentacoesArquivadasPage() {
  const { data: movimentacoes, isLoading, isError, refetch } = useMovimentacoesArquivadas()
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)
  const { restaurar, isLoading: restaurando } = useRestaurarMovimentacao()
  const [excluindo, setExcluindo] = useState<MovimentacaoArquivadaResponse | null>(null)
  const [detalheId, setDetalheId] = useState<string | null>(null)

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map((i) => <Skeleton key={i} width="100%" height="64px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!movimentacoes || movimentacoes.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhuma movimentação arquivada" mensagem="Movimentações arquivadas aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {movimentacoes.map((m) => (
          <div key={m.id} className={styles.linha} onClick={() => setDetalheId(m.id)}>
            <div className={styles.info}>
              <span className={`${styles.iconeBox} ${styles[varianteTipo(m.tipo)]}`}>
                <IconeTipo tipo={m.tipo} />
              </span>
              <div>
                <p className={styles.nome}>{m.descricao || rotuloTipo(m.tipo)}</p>
                <p className={styles.detalhe}>{formatarMoeda(m.valor)} · {formatarData(m.dataMovimentacao)}</p>
              </div>
            </div>
            <div className={styles.acoes} onClick={(e) => e.stopPropagation()}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(m.id, m.descricao || rotuloTipo(m.tipo))}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              <button className={styles.botaoExcluir} onClick={() => setExcluindo(m)}>
                <Trash2 size={14} /> Excluir definitivamente
              </button>
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo movimentacao={excluindo} onClose={() => setExcluindo(null)} />
      )}

      {detalheId && (
        <DrawerDetalheMovimentacao movimentacaoId={detalheId} onClose={() => setDetalheId(null)} />
      )}
    </>
  )
}

function ModalExcluirDefinitivo({ movimentacao, onClose }: { movimentacao: MovimentacaoArquivadaResponse; onClose: () => void }) {
  const { confirmar, isLoading, erroGeral } = useExcluirMovimentacaoDefinitivamente(movimentacao, onClose)

  const identificacao = movimentacao.descricao
    ? `${movimentacao.descricao} (${formatarMoeda(movimentacao.valor)})`
    : `${rotuloTipo(movimentacao.tipo)} de ${formatarMoeda(movimentacao.valor)} em ${formatarData(movimentacao.dataMovimentacao)}`

  // Some das listagens/relatórios já foi avisado (e confirmado por escrito) no arquivar —
  // aqui só é preciso pedir confirmação por escrito quando tem contribuinte, porque aí sim
  // tem algo NOVO sendo perdido (o vínculo, que sobrevive enquanto só arquivada).
  if (!movimentacao.temContribuinte) {
    return (
      <ModalConfirmacao
        titulo="Excluir movimentação definitivamente?"
        mensagem={<>Isso vai apagar <strong>{identificacao}</strong> de vez. Não tem como desfazer.</>}
        textoConfirmar="Excluir"
        perigo
        isLoading={isLoading}
        onConfirmar={confirmar}
        onClose={onClose}
      />
    )
  }

  const consequencias: Consequencia[] = [{
    tipo: 'perde',
    texto: 'O vínculo dos contribuintes com esta movimentação some junto (as pessoas continuam cadastradas normalmente — só o registro de quem contribuiu aqui é apagado)',
  }]

  return (
    <ModalConfirmacaoCritica
      titulo="Excluir movimentação definitivamente?"
      mensagem={
        <>
          Isso vai apagar <strong>{identificacao}</strong> de vez. Não tem como desfazer.
        </>
      }
      consequencias={consequencias}
      palavraConfirmacao="confirmar"
      textoConfirmar="Excluir definitivamente"
      isLoading={isLoading}
      erro={erroGeral}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
