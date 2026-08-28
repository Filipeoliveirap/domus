'use client'

import { useState } from 'react'
import Link from 'next/link'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useResumo } from '@/hooks/financeiro/relatorio/useResumo'
import { usePorCategoria } from '@/hooks/financeiro/relatorio/usePorCategoria'
import { useEvolucaoMensal } from '@/hooks/financeiro/relatorio/useEvolucaoMensal'
import { calcularPeriodo, ROTULOS_PRESET, type PresetPeriodo } from '@/lib/formats/financeiro/periodoRelatorio'
import { CardsResumo } from './CardsResumo'
import { BarraProporcao } from './BarraProporcao'
import { Destaques } from './Destaques'
import { BreakdownCategoria } from './BreakdownCategoria'
import { BreakdownContribuinte } from './BreakdownContribuinte'
import { usePorContribuinte } from '@/hooks/financeiro/relatorio/usePorContribuinte'
import { GraficoEvolucao } from './GraficoEvolucao'
import type { PeriodoRelatorio } from '@/types/financeiro/relatorio.type'
import { useMaiorLancamento } from '@/hooks/financeiro/relatorio/useMaiorLancamento'
import styles from './relatorios.module.css'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { useConsolidado, useVinculoStatus } from '@/hooks/igreja/useVinculo'
import { VisaoGeralCongregacoes } from './VisaoGeralCongregacoes'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import { PainelFiltros, GrupoFiltro } from '@/components/common/PainelFiltros/PainelFiltros'
import type { Vinculo } from '@/types/pessoa.type'
import {
  SkeletonCardsResumo,
  SkeletonBarraProporcao,
  SkeletonDestaques,
  SkeletonBreakdownCategoria,
  SkeletonGraficoEvolucao,
} from './SkeletonRelatorios'

const PRESETS: PresetPeriodo[] = ['ESTE_MES', 'MES_ANTERIOR', 'ULTIMOS_3_MESES', 'ULTIMOS_6_MESES', 'ESTE_ANO']

const GRUPOS_FILTRO: GrupoFiltro[] = [
  {
    chave: 'vinculo',
    titulo: 'Vínculo de quem contribuiu',
    opcoes: [
      { valor: 'MEMBRO', label: 'Membros' },
      { valor: 'CONGREGANTE', label: 'Congregantes' },
    ],
  },
]

type Aba = 'MINHA_IGREJA' | 'CONGREGACOES'

const idAba = (valor: Aba) => `aba-relatorios-${valor}`
const ID_PAINEL_ABAS = 'painel-relatorios'

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
  const { filtros, setFiltros } = useFiltrosUrl({ vinculo: '' })
  const vinculoFiltro = filtros.vinculo as Vinculo | ''
  // Guarda id E nome: derivar o nome de `consolidado.data` fazia o título sumir durante o
  // refetch (trocar o período), deixando os valores financeiros na tela sem dizer de quem são.
  const [igrejaSelecionada, setIgrejaSelecionada] = useState<{ id: string; nome: string } | null>(null)
  const { congregacao } = useRotulos()

  const ABAS: { valor: Aba; rotulo: string }[] = [
    { valor: 'MINHA_IGREJA', rotulo: 'Minha igreja' },
    { valor: 'CONGREGACOES', rotulo: congregacao.plural },
  ]

  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)

  const periodo: PeriodoRelatorio =
    custom && dataInicio && dataFim
      ? { dataInicio, dataFim }
      : calcularPeriodo(preset)

  const vinculo = useVinculoStatus(autorizado)
  // A aba só existe para quem é mãe — quem não tem congregação não teria o que ver nela.
  const ehMae = vinculo.data?.estado === 'MAE'

  // Derivado, não sincronizado por efeito: se `ehMae` virar false com aba='CONGREGACOES', cai direto pra MINHA_IGREJA sem useEffect.
  const abaEfetiva: Aba = ehMae ? aba : 'MINHA_IGREJA'
  const selecao = ehMae ? igrejaSelecionada : null

  // Só escopa numa congregação quando estamos na aba dela e uma linha foi escolhida.
  const igrejaDoRelatorio =
    abaEfetiva === 'CONGREGACOES' && selecao ? selecao.id : undefined

  // Na aba Congregações sem igreja escolhida, mostramos a visão geral — não os 4 relatórios.
  const mostrandoRelatoriosFinanceiros = abaEfetiva === 'MINHA_IGREJA' || !!selecao

  // Muda quando período/aba/igreja/filtro muda → força as seções a reanimar a entrada
  // (crossfade suave em vez de troca seca dos dados).
  const chaveRelatorio = `${JSON.stringify(periodo)}|${vinculoFiltro}|${abaEfetiva}|${igrejaDoRelatorio ?? ''}`
  const habilitado = autorizado && mostrandoRelatoriosFinanceiros

  const resumo = useResumo(periodo, habilitado, igrejaDoRelatorio, vinculoFiltro)
  const categorias = usePorCategoria(periodo, habilitado, igrejaDoRelatorio, vinculoFiltro)
  const evolucao = useEvolucaoMensal(periodo, habilitado, igrejaDoRelatorio, vinculoFiltro)
  const maiorLanc = useMaiorLancamento(periodo, habilitado, igrejaDoRelatorio, vinculoFiltro)
  const porContribuinte = usePorContribuinte(periodo, habilitado, igrejaDoRelatorio, vinculoFiltro)

  const consolidado = useConsolidado(periodo, autorizado && abaEfetiva === 'CONGREGACOES')

  function escolherPreset(p: PresetPeriodo) {
    setPreset(p)
    setCustom(false)
  }

  function trocarAba(nova: Aba) {
    setAba(nova)
    setIgrejaSelecionada(null)
  }

  // Navegação por setas entre abas (WAI-ARIA Tabs pattern): move o foco E troca a aba —
  // não só uma das duas, senão o teclado e o mouse ficam com comportamentos diferentes.
  function aoNavegarAbas(e: React.KeyboardEvent<HTMLDivElement>) {
    const indiceAtual = ABAS.findIndex((a) => a.valor === abaEfetiva)
    let proximoIndice: number | null = null
    if (e.key === 'ArrowRight') proximoIndice = (indiceAtual + 1) % ABAS.length
    else if (e.key === 'ArrowLeft') proximoIndice = (indiceAtual - 1 + ABAS.length) % ABAS.length
    else if (e.key === 'Home') proximoIndice = 0
    else if (e.key === 'End') proximoIndice = ABAS.length - 1
    if (proximoIndice === null) return

    e.preventDefault()
    const proxima = ABAS[proximoIndice].valor
    trocarAba(proxima)
    document.getElementById(idAba(proxima))?.focus()
  }

  if (!hidratado) {
    return <PaginaCarregando />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  const conteudoDaAba = (
    <>
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
          <Transicao key={`resumo|${chaveRelatorio}|${resumo.isLoading}`} modo="subir">
            <CardsResumo data={resumo.data} isLoading={resumo.isLoading} isError={resumo.isError} aoTentarNovamente={() => resumo.refetch()} />
          </Transicao>

          <Transicao key={`proporcao|${chaveRelatorio}|${resumo.isLoading}`} modo="subir">
            <BarraProporcao data={resumo.data} isLoading={resumo.isLoading} isError={resumo.isError} aoTentarNovamente={() => resumo.refetch()} />
          </Transicao>

          <Transicao key={`destaques|${chaveRelatorio}|${resumo.isLoading || categorias.isLoading}`} modo="subir">
            <Destaques
              resumo={resumo.data}
              categorias={categorias.data}
              maiorLancamento={maiorLanc.data}
              isLoading={resumo.isLoading || categorias.isLoading}
              isError={resumo.isError}
              aoTentarNovamente={() => resumo.refetch()}
            />
          </Transicao>

          <Transicao key={`categoria|${chaveRelatorio}|${categorias.isLoading}`} modo="subir">
            <BreakdownCategoria data={categorias.data} isLoading={categorias.isLoading} isError={categorias.isError} aoTentarNovamente={() => categorias.refetch()} />
          </Transicao>

          <Transicao key={`evolucao|${chaveRelatorio}|${evolucao.isLoading}`} modo="subir">
            <GraficoEvolucao data={evolucao.data} isLoading={evolucao.isLoading} isError={evolucao.isError} aoTentarNovamente={() => evolucao.refetch()} />
          </Transicao>

          <Link href="/financeiro/relatorios/balancete" className={styles.linkBalancete}>
            Ver balancete anual →
          </Link>

          <Transicao key={`contribuinte|${chaveRelatorio}|${porContribuinte.isLoading}`} modo="subir">
            <BreakdownContribuinte
              data={porContribuinte.data}
              isLoading={porContribuinte.isLoading}
              isError={porContribuinte.isError}
              aoTentarNovamente={() => porContribuinte.refetch()}
            />
          </Transicao>
        </>
      )}
    </>
  )

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>Relatórios</h1>
          <p className={styles.subtitulo}>Análise das movimentações financeiras.</p>
        </div>
        <PainelFiltros
          grupos={GRUPOS_FILTRO}
          valores={filtros}
          onAplicar={(v) => setFiltros(v as { vinculo: string })}
        />
      </header>

      {ehMae && (
        <div className={styles.abas} role="tablist" onKeyDown={aoNavegarAbas}>
          {ABAS.map(({ valor, rotulo }) => (
            <button
              key={valor}
              id={idAba(valor)}
              role="tab"
              type="button"
              aria-selected={abaEfetiva === valor}
              aria-controls={ID_PAINEL_ABAS}
              tabIndex={abaEfetiva === valor ? 0 : -1}
              className={`${styles.aba} ${abaEfetiva === valor ? styles.abaAtiva : ''}`}
              onClick={() => trocarAba(valor)}
            >
              {rotulo}
            </button>
          ))}
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
              <CampoData
                semLabel
                value={dataInicio}
                onChange={setDataInicio}
              />
            </div>
            <div className={styles.customCampo}>
              <label className={styles.customLabel}>ATÉ</label>
              <CampoData
                semLabel
                value={dataFim}
                onChange={setDataFim}
              />
            </div>
          </div>
        )}
      </div>

      {ehMae ? (
        <div
          role="tabpanel"
          id={ID_PAINEL_ABAS}
          aria-labelledby={idAba(abaEfetiva)}
          className={styles.pagina}
          // tabIndex 0: um painel de aba precisa ser alcançável por teclado mesmo quando
          // não tem nenhum elemento focável dentro (ex.: só texto/gráfico, sem botão).
          tabIndex={0}
        >
          {conteudoDaAba}
        </div>
      ) : (
        <div className={styles.pagina}>{conteudoDaAba}</div>
      )}
    </div>
  )
}
