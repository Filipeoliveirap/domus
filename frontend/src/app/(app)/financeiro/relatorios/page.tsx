'use client'

import { useState } from 'react'
import { useResumo } from '@/hooks/financeiro/relatorio/useResumo'
import { usePorCategoria } from '@/hooks/financeiro/relatorio/usePorCategoria'
import { useEvolucaoMensal } from '@/hooks/financeiro/relatorio/useEvolucaoMensal'
import { calcularPeriodo, ROTULOS_PRESET, type PresetPeriodo } from '@/lib/formats/financeiro/periodoRelatorio'
import { CardsResumo } from './CardsResumo'
import { BarraProporcao } from './BarraProporcao'
import { Destaques } from './Destaques'
import { BreakdownCategoria } from './BreakdownCategoria'
import { GraficoEvolucao } from './GraficoEvolucao'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'
import { useMaiorLancamento } from '@/hooks/financeiro/relatorio/useMaiorLancamento'
import styles from './relatorios.module.css'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import {
  SkeletonCardsResumo,
  SkeletonBarraProporcao,
  SkeletonDestaques,
  SkeletonBreakdownCategoria,
  SkeletonGraficoEvolucao,
} from './SkeletonRelatorios'

const PRESETS: PresetPeriodo[] = ['ESTE_MES', 'MES_ANTERIOR', 'ULTIMOS_3_MESES', 'ULTIMOS_6_MESES', 'ESTE_ANO']

function PaginaCarregando() {
  return (
    <div className={styles.pagina}>
      <SkeletonCardsResumo />
      <SkeletonBarraProporcao />
      <SkeletonDestaques />
      <SkeletonBreakdownCategoria />
      <SkeletonGraficoEvolucao />
    </div>
  )
}

export default function RelatoriosPage() {
  const [preset, setPreset] = useState<PresetPeriodo>('ESTE_MES')
  const [custom, setCustom] = useState(false)
  const [dataInicio, setDataInicio] = useState('')
  const [dataFim, setDataFim] = useState('')
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = role === 'ADMIN_IGREJA'

  const periodo: PeriodoRelatorio =
    custom && dataInicio && dataFim
      ? { dataInicio, dataFim }
      : calcularPeriodo(preset)

  const resumo = useResumo(periodo, autorizado)
  const categorias = usePorCategoria(periodo, autorizado)
  const evolucao = useEvolucaoMensal(periodo, autorizado)
  const maiorLanc = useMaiorLancamento(periodo, autorizado)

  function escolherPreset(p: PresetPeriodo) {
    setPreset(p)
    setCustom(false)
  }

  if (!hidratado) {
    return <PaginaCarregando />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>Relatórios</h1>
          <p className={styles.subtitulo}>Análise das movimentações financeiras.</p>
        </div>
      </header>

      <div className={styles.filtroPeriodo}>
        <div className={styles.presets}>
          {PRESETS.map((p) => (
            <button
              key={p}
              className={`${styles.botaoPeriodo} ${!custom && preset === p ? styles.periodoAtivo : ''}`}
              onClick={() => escolherPreset(p)}
            >
              {ROTULOS_PRESET[p]}
            </button>
          ))}
          <button
            className={`${styles.botaoPeriodo} ${custom ? styles.periodoAtivo : ''}`}
            onClick={() => setCustom(true)}
          >
            Personalizado
          </button>
        </div>

        {custom && (
          <div className={styles.customDatas}>
            <div className={styles.customCampo}>
              <label className={styles.customLabel}>DE</label>
              <input
                type="date"
                className={styles.customInput}
                value={dataInicio}
                onChange={(e) => setDataInicio(e.target.value)}
              />
            </div>
            <div className={styles.customCampo}>
              <label className={styles.customLabel}>ATÉ</label>
              <input
                type="date"
                className={styles.customInput}
                value={dataFim}
                onChange={(e) => setDataFim(e.target.value)}
              />
            </div>
          </div>
        )}
      </div>

      <CardsResumo data={resumo.data} isLoading={resumo.isLoading} isError={resumo.isError} aoTentarNovamente={() => resumo.refetch()} />

      <BarraProporcao data={resumo.data} isLoading={resumo.isLoading} isError={resumo.isError} aoTentarNovamente={() => resumo.refetch()} />

      <Destaques
        resumo={resumo.data}
        categorias={categorias.data}
        maiorLancamento={maiorLanc.data}
        isLoading={resumo.isLoading || categorias.isLoading}
        isError={resumo.isError}
        aoTentarNovamente={() => resumo.refetch()}
      />

      <BreakdownCategoria data={categorias.data} isLoading={categorias.isLoading} isError={categorias.isError} aoTentarNovamente={() => categorias.refetch()} />

      <GraficoEvolucao data={evolucao.data} isLoading={evolucao.isLoading} isError={evolucao.isError} aoTentarNovamente={() => evolucao.refetch()} />
    </div>
  )
}