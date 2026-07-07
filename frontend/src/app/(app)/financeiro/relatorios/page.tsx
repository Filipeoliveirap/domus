'use client'

import { useState } from 'react'
import { useResumo } from '@/hooks/financeiro/relatorio/useResumo'
import { usePorCategoria } from '@/hooks/financeiro/relatorio/usePorCategoria'
import { useEvolucaoMensal } from '@/hooks/financeiro/relatorio/useEvolucaoMensal'
import { calcularPeriodo, ROTULOS_PRESET, type PresetPeriodo } from '@/lib/formats/financeiro/periodoRelatorio'
import { CardsResumo } from '@/app/(app)/financeiro/relatorios/CardsResumo'
import { BreakdownCategoria } from './BreakdownCategoria'
import { GraficoEvolucao } from './GraficoEvolucao'
import styles from './relatorios.module.css'

const PRESETS: PresetPeriodo[] = ['ESTE_MES', 'MES_ANTERIOR', 'ESTE_ANO']

export default function RelatoriosPage() {
  const [preset, setPreset] = useState<PresetPeriodo>('ESTE_MES')
  const periodo = calcularPeriodo(preset)

  const resumo = useResumo(periodo)
  const categorias = usePorCategoria(periodo)
  const evolucao = useEvolucaoMensal(periodo)

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>Relatórios</h1>
          <p className={styles.subtitulo}>Análise das movimentações financeiras.</p>
        </div>
        <div className={styles.seletorPeriodo}>
          {PRESETS.map((p) => (
            <button
              key={p}
              className={`${styles.botaoPeriodo} ${preset === p ? styles.periodoAtivo : ''}`}
              onClick={() => setPreset(p)}
            >
              {ROTULOS_PRESET[p]}
            </button>
          ))}
        </div>
      </header>

      {/* Cards de resumo */}
      <CardsResumo
        data={resumo.data}
        isLoading={resumo.isLoading}
        isError={resumo.isError}
      />

      {/* Breakdown por categoria */}
      <BreakdownCategoria
        data={categorias.data}
        isLoading={categorias.isLoading}
        isError={categorias.isError}
      />

      {/* Gráfico de evolução */}
      <GraficoEvolucao
        data={evolucao.data}
        isLoading={evolucao.isLoading}
        isError={evolucao.isError}
      />
    </div>
  )
}