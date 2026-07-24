'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { ChevronRight } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { podeVerListaCompletaDeInscritos } from '@/lib/permissoes'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { useTiposEvento } from '@/hooks/evento/useTiposEvento'
import { useRelatorioGeral } from '@/hooks/evento/useRelatorioGeral'
import { PainelFiltros, GrupoFiltro } from '@/components/common/PainelFiltros/PainelFiltros'
import { RECORTES_ETARIOS } from '@/components/module/eventos/BlocoParaQuemE'
import { CampoData } from '@/components/common/CampoData/CampoData'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { GraficoTendenciaComparecimento } from '@/components/module/eventos/GraficoTendenciaComparecimento'
import { CardVariacao } from '@/components/module/eventos/CardVariacao'
import { SkeletonRelatorioGeral } from './SkeletonRelatorioGeral'
import styles from './relatorio.module.css'

const OPCOES_RECORTE = RECORTES_ETARIOS.map((r) => ({ valor: r.nome, label: r.nome }))

function RelatorioGeralConteudo() {
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const autorizado = podeVerListaCompletaDeInscritos(role)

  const { filtros, setFiltros } = useFiltrosUrl({ tipo: '', recorteEtario: '' })
  const [periodo, setPeriodo] = useState({ inicio: '', fim: '' })
  const [pagina, setPagina] = useState(0)

  const { data: tipos = [] } = useTiposEvento()

  const gruposFiltro: GrupoFiltro[] = [
    { chave: 'tipo', titulo: 'Tipo', opcoes: tipos.map((t) => ({ valor: t, label: t })) },
    { chave: 'recorteEtario', titulo: 'Recorte etário', opcoes: OPCOES_RECORTE },
  ]

  const { data: relatorio, isLoading, isError, refetch } = useRelatorioGeral({
    inicio: periodo.inicio ? `${periodo.inicio}T00:00:00` : undefined,
    fim: periodo.fim ? `${periodo.fim}T23:59:59` : undefined,
    tipo: filtros.tipo || undefined,
    recorteEtario: filtros.recorteEtario || undefined,
    page: pagina,
  })

  if (!hidratado) {
    return <div className={styles.pagina} />
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  function aoAplicarFiltros(valores: Record<string, string>) {
    setFiltros(valores as { tipo: string; recorteEtario: string })
    setPagina(0)
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <Link href="/eventos" className={styles.breadcrumbLink}>Eventos</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Relatório de engajamento</span>
      </nav>

      <header className={styles.cabecalho}>
        <h1 className={styles.titulo}>Relatório de Engajamento</h1>

        <div className={styles.filtros}>
          <div className={styles.filtroData}>
            <CampoData
              id="periodo-inicio"
              label="DE"
              value={periodo.inicio}
              onChange={(iso) => { setPeriodo((p) => ({ ...p, inicio: iso })); setPagina(0) }}
            />
          </div>
          <div className={styles.filtroData}>
            <CampoData
              id="periodo-fim"
              label="ATÉ"
              value={periodo.fim}
              onChange={(iso) => { setPeriodo((p) => ({ ...p, fim: iso })); setPagina(0) }}
            />
          </div>
          <PainelFiltros grupos={gruposFiltro} valores={filtros} onAplicar={aoAplicarFiltros} />
        </div>
      </header>

      {isLoading ? (
        <SkeletonRelatorioGeral />
      ) : isError || !relatorio ? (
        <EstadoErro
          titulo="Não foi possível carregar o relatório"
          mensagem="Verifique sua conexão e tente novamente."
          aoTentarNovamente={() => refetch()}
        />
      ) : (
        <>
          <div className={styles.resumoGrade}>
            <div className={styles.resumoCard}>
              <span className={styles.resumoValor}>{relatorio.resumo.totalEventos}</span>
              <span className={styles.resumoLabel}>Total de eventos</span>
            </div>

            <div className={styles.resumoCard}>
              {relatorio.resumo.comparecimentoMedio == null ? (
                <span className={styles.resumoSemDado}>Sem dado de presença no período</span>
              ) : (
                <span className={styles.resumoValor}>{relatorio.resumo.comparecimentoMedio}</span>
              )}
              <span className={styles.resumoLabel}>Comparecimento médio</span>
            </div>

            <div className={styles.resumoCard}>
              {relatorio.resumo.participantesUnicos == null ? (
                <span className={styles.resumoSemDado}>Sem dado de presença no período</span>
              ) : (
                <span className={styles.resumoValor}>{relatorio.resumo.participantesUnicos}</span>
              )}
              <span className={styles.resumoLabel}>Participantes únicos</span>
            </div>

            <div className={styles.resumoCard}>
              {relatorio.eventoMaisPopular ? (
                <>
                  <span className={styles.resumoPopularTitulo}>{relatorio.eventoMaisPopular.titulo}</span>
                  <span className={styles.resumoLabel}>
                    {relatorio.eventoMaisPopular.totalInscritos} inscritos — evento mais popular
                  </span>
                </>
              ) : (
                <span className={styles.resumoSemDado}>Nenhum evento no período</span>
              )}
            </div>
          </div>

          <section className={styles.secao}>
            <h2 className={styles.secaoTitulo}>Tendência de comparecimento (6 meses)</h2>
            <GraficoTendenciaComparecimento tendencia={relatorio.tendencia} />
          </section>

          <section className={styles.secao}>
            <h2 className={styles.secaoTitulo}>Últimos Eventos</h2>
            {relatorio.ultimosEventos.content.length === 0 ? (
              <p className={styles.resumoSemDado}>Nenhum evento no período.</p>
            ) : (
              <>
                <div className={styles.listaEventos}>
                  {relatorio.ultimosEventos.content.map((evento) => (
                    <div key={evento.eventoId} className={styles.linhaEvento}>
                      <div className={styles.linhaEventoInfo}>
                        <span className={styles.linhaEventoTitulo}>{evento.titulo}</span>
                        <span className={styles.linhaEventoData}>
                          {new Date(evento.data).toLocaleDateString('pt-BR')}
                        </span>
                      </div>
                      <span className={styles.linhaEventoParticipantes}>
                        {evento.totalParticipantes} participantes
                      </span>
                      <div className={styles.linhaEventoVariacoes}>
                        {evento.variacaoEventoAnterior && (
                          <CardVariacao variacao={evento.variacaoEventoAnterior} rotulo="vs. anterior" />
                        )}
                        <CardVariacao variacao={evento.variacaoMediaGeral} rotulo="vs. média do filtro" />
                      </div>
                    </div>
                  ))}
                </div>

                {relatorio.ultimosEventos.totalPages > 1 && (
                  <footer className={styles.rodape}>
                    <span className={styles.contagem}>
                      Exibindo {relatorio.ultimosEventos.content.length} de {relatorio.ultimosEventos.totalElements} eventos
                    </span>
                    <div className={styles.paginacao}>
                      <button
                        type="button"
                        onClick={() => setPagina((p) => Math.max(0, p - 1))}
                        disabled={pagina === 0}
                        className={styles.botaoPagina}
                      >‹</button>
                      <span className={styles.infoPagina}>
                        Página {pagina + 1} de {relatorio.ultimosEventos.totalPages}
                      </span>
                      <button
                        type="button"
                        onClick={() => setPagina((p) => p + 1)}
                        disabled={pagina + 1 >= relatorio.ultimosEventos.totalPages}
                        className={styles.botaoPagina}
                      >›</button>
                    </div>
                  </footer>
                )}
              </>
            )}
          </section>
        </>
      )}
    </div>
  )
}

export default function RelatorioGeralPage() {
  return (
    <Suspense fallback={<div className={styles.pagina} />}>
      <RelatorioGeralConteudo />
    </Suspense>
  )
}
