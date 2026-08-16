'use client'

import { TrendingUp, TrendingDown, Wallet, ArrowUp, ArrowDown } from 'lucide-react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { formatarVariacao } from '@/lib/formats/financeiro/relatorioFormat'
import type { ResumoPeriodo } from '@/types/financeiro/relatorio.type'
import styles from './CardsResumo.module.css'
import { SkeletonCardsResumo } from "./SkeletonRelatorios";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

interface CardsResumoProps {
  data: ResumoPeriodo | undefined
  isLoading: boolean
  isError: boolean
  aoTentarNovamente: () => void
}

export function CardsResumo({ data, isLoading, isError, aoTentarNovamente }: CardsResumoProps) {
  if (isLoading) {
    return <SkeletonCardsResumo />;
  }

  if (isError || !data) {
    return (
    <EstadoErro
      titulo="Não foi possível carregar o resumo"
      mensagem="Tente novamente."
      aoTentarNovamente={aoTentarNovamente}
    />
  )
  }

  const entradas = formatarVariacao(data.comparacao.entradasVariacao)
  const saidas = formatarVariacao(data.comparacao.saidasVariacao)
  const saldoVar = formatarVariacao(data.comparacao.saldoVariacao)

  return (
    <div className={styles.grid}>
      {/* Entradas */}
      <div className={`${styles.card} ${styles.cardEntrada}`}>
        <div className={styles.cardTopo}>
          <span className={styles.cardLabel}>Total de Entradas</span>
          <span className={styles.cardIcone}><TrendingUp size={20} /></span>
        </div>
        <p className={styles.cardValor}>{formatarMoeda(data.totalEntradas)}</p>
        <div className={`${styles.variacao} ${styles[entradas.sinal]}`}>
          {entradas.sinal === 'positivo' && <ArrowUp size={14} />}
          {entradas.sinal === 'negativo' && <ArrowDown size={14} />}
          <span>{entradas.texto} vs período anterior</span>
        </div>
        <span className={styles.cardSub}>Anterior: {formatarMoeda(data.entradasAnterior)}</span>
      </div>

      {/* Saídas */}
      <div className={`${styles.card} ${styles.cardSaida}`}>
        <div className={styles.cardTopo}>
          <span className={styles.cardLabel}>Total de Saídas</span>
          <span className={styles.cardIcone}><TrendingDown size={20} /></span>
        </div>
        <p className={styles.cardValor}>{formatarMoeda(data.totalSaidas)}</p>
        <div className={`${styles.variacao} ${styles[saidas.sinal]}`}>
          {saidas.sinal === 'positivo' && <ArrowUp size={14} />}
          {saidas.sinal === 'negativo' && <ArrowDown size={14} />}
          <span>{saidas.texto} vs período anterior</span>
        </div>
        <span className={styles.cardSub}>Anterior: {formatarMoeda(data.saidasAnterior)}</span>
      </div>

      {/* Saldo — card destacado */}
      <div className={`${styles.card} ${styles.cardSaldo}`}>
        <div className={styles.cardTopo}>
          <span className={styles.cardLabelClaro}>Saldo Consolidado</span>
          <span className={styles.cardIconeClaro}><Wallet size={20} /></span>
        </div>
        <p className={styles.cardValorClaro}>{formatarMoeda(data.saldo)}</p>
        <div className={`${styles.variacao} ${styles[saldoVar.sinal]}`}>
          {saldoVar.sinal === 'positivo' && <ArrowUp size={14} />}
          {saldoVar.sinal === 'negativo' && <ArrowDown size={14} />}
          <span>{saldoVar.texto} vs período anterior</span>
        </div>
        <span className={styles.cardSubClaro}>
          Saldo anterior: {formatarMoeda(data.saldoAnterior)}
        </span>
        <span className={styles.cardSubClaro}>
          {data.quantidadeMovimentacoes} movimentaç{data.quantidadeMovimentacoes === 1 ? 'ão' : 'ões'} no período
        </span>
      </div>
    </div>
  )
}