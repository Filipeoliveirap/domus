import { Award, Receipt, TrendingUp } from 'lucide-react'
import { formatarMoeda, formatarData } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { ResumoPeriodo, CategoriaBreakdown, MaiorLancamento } from '@/types/financeiro/relatorio.type'
import styles from './Destaques.module.css'

interface DestaquesProps {
  resumo: ResumoPeriodo | undefined
  categorias: CategoriaBreakdown[] | undefined
  maiorLancamento: MaiorLancamento | null | undefined
  isLoading: boolean
}

export function Destaques({ resumo, categorias, maiorLancamento, isLoading }: DestaquesProps) {
  if (isLoading) {
    return (
      <div className={styles.grid}>
        {[0, 1, 2].map((i) => <div key={i} className={styles.skeleton} />)}
      </div>
    )
  }
  if (!resumo || !categorias) return null

  const entradas = categorias.filter((c) => c.tipo === 'ENTRADA')
  const topEntrada = entradas.length > 0
    ? entradas.reduce((max, c) => parseFloat(c.total) > parseFloat(max.total) ? c : max)
    : null

  const totalEntradas = parseFloat(resumo.totalEntradas)
  const qtd = resumo.quantidadeMovimentacoes
  const ticketMedio = qtd > 0 ? totalEntradas / qtd : 0

  return (
    <div className={styles.grid}>
      {/* Categoria que mais arrecadou */}
      <div className={styles.card}>
        <span className={styles.cardIcone}><Award size={20} /></span>
        <div className={styles.cardInfo}>
          <span className={styles.cardLabel}>Categoria que mais arrecadou</span>
          {topEntrada ? (
            <>
              <span className={styles.cardValor}>{topEntrada.categoriaNome}</span>
              <span className={styles.cardSub}>{formatarMoeda(topEntrada.total)}</span>
            </>
          ) : (
            <span className={styles.cardValor}>—</span>
          )}
        </div>
      </div>

      {/* Valor médio */}
      <div className={styles.card}>
        <span className={styles.cardIcone}><Receipt size={20} /></span>
        <div className={styles.cardInfo}>
          <span className={styles.cardLabel}>Valor médio por movimentação</span>
          <span className={styles.cardValor}>{formatarMoeda(String(ticketMedio))}</span>
          <span className={styles.cardSub}>{qtd} movimentaç{qtd === 1 ? 'ão' : 'ões'}</span>
        </div>
      </div>

      {/* Maior lançamento */}
      <div className={styles.card}>
        <span className={styles.cardIcone}><TrendingUp size={20} /></span>
        <div className={styles.cardInfo}>
          <span className={styles.cardLabel}>Maior lançamento</span>
          {maiorLancamento ? (
            <>
              <span className={styles.cardValor}>{formatarMoeda(maiorLancamento.valor)}</span>
              <span className={styles.cardSub}>
                {maiorLancamento.categoriaNome} · {formatarData(maiorLancamento.dataMovimentacao)}
              </span>
            </>
          ) : (
            <span className={styles.cardValor}>—</span>
          )}
        </div>
      </div>
    </div>
  )
}