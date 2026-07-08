'use client'

import { TrendingUp, TrendingDown, Wallet, ArrowUp, ArrowDown } from 'lucide-react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import { formatarVariacao } from '@/lib/formats/financeiro/relatorioFormat'
import type { ResumoPeriodo } from '@/types/financeiro/relatorio.type'
import styles from './CardsResumo.module.css'

interface CardsResumoProps {
  data: ResumoPeriodo | undefined
  isLoading: boolean
  isError: boolean
}

export function CardsResumo({ data, isLoading, isError }: CardsResumoProps) {
  if (isLoading) {
    return (
      <div className={styles.grid}>
        {[0, 1, 2].map((i) => <div key={i} className={styles.skeleton} />)}
      </div>
    )
  }

  if (isError || !data) {
    return <div className={styles.erro}>Não foi possível carregar o resumo.</div>
  }

  const entradas = formatarVariacao(data.comparacao.entradasVariacao)
  const saidas = formatarVariacao(data.comparacao.saidasVariacao)

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
      </div>

      {/* Saldo — card destacado */}
      <div className={`${styles.card} ${styles.cardSaldo}`}>
        <div className={styles.cardTopo}>
          <span className={styles.cardLabelClaro}>Saldo Consolidado</span>
          <span className={styles.cardIconeClaro}><Wallet size={20} /></span>
        </div>
        <p className={styles.cardValorClaro}>{formatarMoeda(data.saldo)}</p>
        <span className={styles.cardSubClaro}>
          {data.quantidadeMovimentacoes} movimentaç{data.quantidadeMovimentacoes === 1 ? 'ão' : 'ões'} no período
        </span>
      </div>
    </div>
  )
}