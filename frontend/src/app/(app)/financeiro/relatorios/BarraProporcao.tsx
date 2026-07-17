'use client'

import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { ResumoPeriodo } from '@/types/financeiro/relatorio.type'
import styles from './BarraProporcao.module.css'
import { SkeletonBarraProporcao } from "./SkeletonRelatorios";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
interface BarraProporcaoProps {
  data: ResumoPeriodo | undefined
  isLoading: boolean
  isError?: boolean
  aoTentarNovamente?: () => void
}

export function BarraProporcao({ data, isLoading, isError, aoTentarNovamente }: BarraProporcaoProps) {
  if (isLoading) {
    return <SkeletonBarraProporcao />;
  }

  if (isError || !data) {
  return (
    <EstadoErro
      titulo="Não foi possível carregar a proporção de entradas e saídas"
      mensagem="Tente novamente."
      aoTentarNovamente={aoTentarNovamente}
    />
  )
}

  const entradas = parseFloat(data.totalEntradas)
  const saidas = parseFloat(data.totalSaidas)

  const percentualUtilizado = entradas > 0 ? Math.min((saidas / entradas) * 100, 100) : 0
  const pctTexto = percentualUtilizado.toFixed(0)

  return (
    <div className={styles.painel}>
      <div className={styles.header}>
        <div>
          <h3 className={styles.titulo}>Proporção Entradas vs Saídas</h3>
          <p className={styles.subtitulo}>Percentual da receita comprometido com despesas</p>
        </div>
        <div className={styles.pctDestaque}>
          <span className={styles.pctNumero}>{pctTexto}%</span>
          <span className={styles.pctLabel}>da receita utilizada</span>
        </div>
      </div>

      {/* Barra */}
      <div className={styles.barra}>
        <div
          className={styles.barraEntrada}
          style={{ width: `${100 - percentualUtilizado}%` }}
        />
        <div
          className={styles.barraSaida}
          style={{ width: `${percentualUtilizado}%` }}
        />
      </div>

      <div className={styles.legenda}>
        <span className={styles.legendaItem}>
          <span className={`${styles.legendaDot} ${styles.dotEntrada}`} />
          Entradas ({formatarMoeda(data.totalEntradas)})
        </span>
        <span className={styles.legendaItem}>
          <span className={`${styles.legendaDot} ${styles.dotSaida}`} />
          Saídas ({formatarMoeda(data.totalSaidas)})
        </span>
      </div>
    </div>
  )
}