'use client'

import { ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { formatarMoeda } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { CategoriaBreakdown } from '@/types/financeiro/relatorio.type'
import styles from './BreakdownCategoria.module.css'
import { SkeletonBreakdownCategoria } from "./SkeletonRelatorios";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

interface BreakdownCategoriaProps {
  data: CategoriaBreakdown[] | undefined
  isLoading: boolean
  isError: boolean
  aoTentarNovamente?: () => void
}

export function BreakdownCategoria({ data, isLoading, isError, aoTentarNovamente }: BreakdownCategoriaProps) {
  if (isLoading) {
    return <SkeletonBreakdownCategoria />;
  }

  if (isError || !data) {
    return (
      <EstadoErro
        titulo="Não foi possível carregar o detalhamento por categoria"
        mensagem="Tente novamente."
        aoTentarNovamente={aoTentarNovamente}
      />
    )
  }

  const entradas = data.filter((c) => c.tipo === 'ENTRADA')
  const saidas = data.filter((c) => c.tipo === 'SAIDA')

  return (
    <div className={styles.grid}>
      {/* Entradas por categoria */}
      <div className={styles.coluna}>
        <div className={styles.colunaHeader}>
          <span className={`${styles.headerIcone} ${styles.entrada}`}><ArrowDownCircle size={18} /></span>
          <h3 className={styles.colunaTitulo}>Entradas por Categoria</h3>
        </div>
        <Tabela itens={entradas} variante="entrada" />
      </div>

      {/* Saídas por categoria */}
      <div className={styles.coluna}>
        <div className={styles.colunaHeader}>
          <span className={`${styles.headerIcone} ${styles.saida}`}><ArrowUpCircle size={18} /></span>
          <h3 className={styles.colunaTitulo}>Saídas por Categoria</h3>
        </div>
        <Tabela itens={saidas} variante="saida" />
      </div>
    </div>
  )
}

function Tabela({ itens, variante }: { itens: CategoriaBreakdown[]; variante: 'entrada' | 'saida' }) {
  if (itens.length === 0) {
    return <div className={styles.vazio}>Nenhuma movimentação no período.</div>
  }

  return (
    <div className={styles.tabela}>
      <div className={styles.tabelaHeader}>
        <span>CATEGORIA</span>
        <span className={styles.colValor}>VALOR</span>
        <span className={styles.colPct}>%</span>
      </div>
      {itens.map((item) => (
        <div key={item.categoriaId} className={styles.linha}>
          <span className={styles.catNome}>{item.categoriaNome}</span>
          <span className={styles.colValor}>{formatarMoeda(item.total)}</span>
          <span className={styles.colPct}>
            <span className={`${styles.pctBadge} ${styles[variante]}`}>
              {parseFloat(item.percentual).toFixed(0)}%
            </span>
          </span>
        </div>
      ))}
    </div>
  )
}