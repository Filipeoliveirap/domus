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
import { useConsolidado, useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { VisaoGeralCongregacoes } from './VisaoGeralCongregacoes'
import {
  SkeletonCardsResumo,
  SkeletonBarraProporcao,
  SkeletonDestaques,
  SkeletonBreakdownCategoria,
  SkeletonGraficoEvolucao,
} from './SkeletonRelatorios'

const PRESETS: PresetPeriodo[] = ['ESTE_MES', 'MES_ANTERIOR', 'ULTIMOS_3_MESES', 'ULTIMOS_6_MESES', 'ESTE_ANO']

type Aba = 'MINHA_IGREJA' | 'CONGREGACOES'

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
  const [aba, setAba] = useState<Aba>('MINHA_IGREJA')
  // Guarda id E nome: derivar o nome de `consolidado.data` fazia o título sumir durante o
  // refetch (trocar o período), deixando os valores financeiros na tela sem dizer de quem são.
  const [igrejaSelecionada, setIgrejaSelecionada] = useState<{ id: string; nome: string } | null>(null)

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = role === 'ADMIN_IGREJA'

  const periodo: PeriodoRelatorio =
    custom && dataInicio && dataFim
      ? { dataInicio, dataFim }
      : calcularPeriodo(preset)

  const vinculo = useVinculoStatus(autorizado)
  // A aba só existe para quem é mãe — quem não tem congregação não teria o que ver nela.
  const ehMae = vinculo.data?.estado === 'MAE'

  /*
   * DERIVADO, não sincronizado por efeito. Se a última congregação sair da família com esta
   * tela aberta, `ehMae` vira false e a barra de abas some; sem isto o state continuaria em
   * 'CONGREGACOES' e a visão geral ficaria na tela sem nenhum controle para voltar.
   * Derivar resolve no mesmo render — um useEffect com setState causaria render em cascata.
   */
  const abaEfetiva: Aba = ehMae ? aba : 'MINHA_IGREJA'
  const selecao = ehMae ? igrejaSelecionada : null

  // Só escopa numa congregação quando estamos na aba dela e uma linha foi escolhida.
  const igrejaDoRelatorio =
    abaEfetiva === 'CONGREGACOES' && selecao ? selecao.id : undefined

  // Na aba Congregações sem igreja escolhida, mostramos a visão geral — não os 4 relatórios.
  const mostrandoRelatoriosFinanceiros = abaEfetiva === 'MINHA_IGREJA' || !!selecao
  const habilitado = autorizado && mostrandoRelatoriosFinanceiros

  const resumo = useResumo(periodo, habilitado, igrejaDoRelatorio)
  const categorias = usePorCategoria(periodo, habilitado, igrejaDoRelatorio)
  const evolucao = useEvolucaoMensal(periodo, habilitado, igrejaDoRelatorio)
  const maiorLanc = useMaiorLancamento(periodo, habilitado, igrejaDoRelatorio)

  const consolidado = useConsolidado(periodo, autorizado && abaEfetiva === 'CONGREGACOES')

  function escolherPreset(p: PresetPeriodo) {
    setPreset(p)
    setCustom(false)
  }

  function trocarAba(nova: Aba) {
    setAba(nova)
    setIgrejaSelecionada(null)
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

      {ehMae && (
        <div className={styles.abas} role="tablist">
          <button
            role="tab"
            aria-selected={abaEfetiva === 'MINHA_IGREJA'}
            className={`${styles.aba} ${abaEfetiva === 'MINHA_IGREJA' ? styles.abaAtiva : ''}`}
            onClick={() => trocarAba('MINHA_IGREJA')}
          >
            Minha igreja
          </button>
          <button
            role="tab"
            aria-selected={abaEfetiva === 'CONGREGACOES'}
            className={`${styles.aba} ${abaEfetiva === 'CONGREGACOES' ? styles.abaAtiva : ''}`}
            onClick={() => trocarAba('CONGREGACOES')}
          >
            Congregações
          </button>
        </div>
      )}

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

      {abaEfetiva === 'CONGREGACOES' && !selecao && (
        <VisaoGeralCongregacoes
          data={consolidado.data}
          isLoading={consolidado.isLoading}
          isError={consolidado.isError}
          aoTentarNovamente={() => consolidado.refetch()}
          aoEscolherIgreja={setIgrejaSelecionada}
        />
      )}

      {abaEfetiva === 'CONGREGACOES' && selecao && (
        <div className={styles.barraDetalhe}>
          <button className={styles.botaoVoltar} onClick={() => setIgrejaSelecionada(null)}>
            ← Voltar para a visão geral
          </button>
          <span className={styles.nomeDetalhe}>{selecao.nome}</span>
        </div>
      )}

      {mostrandoRelatoriosFinanceiros && (
        <>
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
        </>
      )}
    </div>
  )
}
