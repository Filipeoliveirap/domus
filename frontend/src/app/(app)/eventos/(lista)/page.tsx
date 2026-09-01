'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { useEventos } from '@/hooks/evento/useEventos'
import { useTiposEvento } from '@/hooks/evento/useTiposEvento'
import { useAuthStore } from '@/store/authStore'
import { podeGerenciarEventos } from '@/lib/permissoes'
import { EventoCard } from '@/components/module/eventos/EventoCard'
import { DrawerDetalheEvento } from '@/app/(app)/eventos/(lista)/(detalhe)/DrawerDetalheEvento'
import { ModalArquivarEvento } from './ModalArquivarEvento'
import { EventoResponse } from '@/types/evento.type'
import { useBuscaUrl } from '@/hooks/busca/useBuscaUrl'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { usePaginaUrl } from '@/hooks/busca/usePaginaUrl'
import { clsx } from 'clsx'
import { PainelFiltros, GrupoFiltro } from '@/components/common/PainelFiltros/PainelFiltros'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { RECORTES_ETARIOS } from '@/components/module/eventos/BlocoParaQuemE'
import styles from './Page.module.css'
import { EstadoVazio } from "@/components/common/EstadoVazio/EstadoVazio";
import { SearchX, Inbox } from 'lucide-react'
import { SkeletonEventos } from "./SkeletonEventos";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

// Recortes prontos (Kids, Jovens…) como opções de filtro — mesma fonte que alimenta o
// selo do card e os chips do formulário, nenhum nome de recorte solto por aqui.
const OPCOES_RECORTE = RECORTES_ETARIOS.map((r) => ({ valor: r.nome, label: r.nome }))

const TAMANHO_PAGINA = 12

function EventosConteudo() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const detalheId = searchParams.get('detalhe')
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarEventos(role)

  const { pagina, setPagina } = usePaginaUrl()
  const { busca, setBusca, buscaDebounced } = useBuscaUrl({ delay: 250 })
  const { filtros, setFiltros } = useFiltrosUrl({ tipo: '', recorteEtario: '' })

  const [eventoArquivando, setEventoArquivando] = useState<EventoResponse | null>(null)
  // Selo de pendência no card foi clicado: o drawer abre já com o modal de resposta aberto.
  const [abrirPendenciaAoMontar, setAbrirPendenciaAoMontar] = useState(false)

  const { data: tipos = [] } = useTiposEvento()

  const gruposFiltro: GrupoFiltro[] = [
    {
      chave: 'tipo',
      titulo: 'Tipo',
      opcoes: tipos.map((t) => ({ valor: t, label: t })),
    },
    {
      chave: 'recorteEtario',
      titulo: 'Para quem é',
      opcoes: OPCOES_RECORTE,
    },
  ]

  const { data, isLoading, isError, isFetching, refetch } = useEventos({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
    tipo: filtros.tipo,
    recorteEtario: filtros.recorteEtario,
  })

  const eventos = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  function aoAplicarFiltros(valores: Record<string, string>) {
    setFiltros(valores as { tipo: string; recorteEtario: string })
    setPagina(0)
  }

  // Muda quando filtro/página muda (busca fica de fora — não re-anima a cada tecla) →
  // o grid faz fade de entrada, como em movimentações financeiras.
  const chaveLista = `${filtros.tipo}|${filtros.recorteEtario}|${pagina}`

  function abrirDetalhe(evento: EventoResponse) {
    router.push(`/eventos?detalhe=${evento.id}`, { scroll: false })
  }
  function abrirDetalheComPendencia(evento: EventoResponse) {
    setAbrirPendenciaAoMontar(true)
    abrirDetalhe(evento)
  }
  function fecharDetalhe() {
    setAbrirPendenciaAoMontar(false)
    router.push('/eventos', { scroll: false })
  }

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <SkeletonEventos cards={TAMANHO_PAGINA} />
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>Eventos</h1>
            {totalElementos > 0 && <span className={styles.contador}>{totalElementos}</span>}
          </div>
          <p className={styles.subtitulo}>Agenda da igreja</p>
        </div>
        {podeGerenciar && (
          <div className={styles.acoesCabecalho}>
            <Link href="/eventos/relatorio" className={styles.linkRelatorio}>
              Relatório de engajamento
            </Link>
            <Link href="/eventos/locais" className={styles.botaoSecundario}>
              Locais
            </Link>
            <Link href="/eventos/cadastrar" className={styles.botaoPrimario}>
              Novo evento
            </Link>
          </div>
        )}
      </header>

      <div className={styles.barraBusca}>
        <input
          type="text"
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar eventos..."
          className={styles.inputBusca}
        />
        <PainelFiltros grupos={gruposFiltro} valores={filtros} onAplicar={aoAplicarFiltros} />
      </div>

      {/* Um só <Transicao> sempre montado (igual movimentações): remonta ao trocar
          filtro/página, mas digitar na busca NÃO remonta — o grid só amortece (opacity)
          enquanto o fetch corre sem re-montar a lista. */}
      <Transicao key={chaveLista} modo="fade" className={styles.conteudoLista}>
        {isLoading ? (
          <SkeletonEventos cards={TAMANHO_PAGINA} />
        ) : isError ? (
          <EstadoErro
            titulo="Não foi possível carregar os eventos"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : eventos.length === 0 ? (
          <EstadoVazio
            icone={buscaDebounced ? SearchX : Inbox}
            titulo={buscaDebounced ? 'Nenhum evento encontrado' : 'Nenhum evento cadastrado'}
            mensagem={
              buscaDebounced
                ? `Não encontramos resultados para "${buscaDebounced}". Tente outro termo.`
                : 'Comece cadastrando o primeiro evento da agenda.'
            }
            acaoSecundaria={buscaDebounced ? { label: 'Limpar busca', onClick: () => setBusca('') } : undefined}
            acaoPrimaria={!buscaDebounced && podeGerenciar ? { label: 'Novo evento', onClick: () => router.push('/eventos/cadastrar') } : undefined}
          />
        ) : (
          <>
            <div className={clsx(styles.grid, isFetching && !isLoading && styles.listaAtualizando)}>
              {eventos.map((evento) => (
                <EventoCard
                  key={evento.id}
                  evento={evento}
                  onAbrirDetalhe={abrirDetalhe}
                  onAbrirPendencia={abrirDetalheComPendencia}
                  onArquivar={setEventoArquivando}
                />
              ))}
            </div>

            <footer className={styles.rodape}>
              <span className={styles.contagem}>
                Exibindo {eventos.length} de {totalElementos} eventos
              </span>
              <div className={styles.paginacao}>
                <button
                  onClick={() => setPagina((p) => Math.max(0, p - 1))}
                  disabled={pagina === 0}
                  className={styles.botaoPagina}
                >‹</button>
                <span className={styles.infoPagina}>
                  Página {pagina + 1} de {totalPaginas}
                </span>
                <button
                  onClick={() => setPagina((p) => p + 1)}
                  disabled={pagina + 1 >= totalPaginas}
                  className={styles.botaoPagina}
                >›</button>
              </div>
            </footer>
          </>
        )}
      </Transicao>

      {detalheId && (
        <DrawerDetalheEvento
          eventoId={detalheId}
          onClose={fecharDetalhe}
          abrirPendenciaAoMontar={abrirPendenciaAoMontar}
        />
      )}

      {eventoArquivando && (
        <ModalArquivarEvento
          evento={eventoArquivando}
          onClose={() => setEventoArquivando(null)}
        />
      )}
    </div>
  )
}

export default function EventosPage() {
  return (
    <Suspense fallback={<div>Carregando…</div>}>
      <EventosConteudo />
    </Suspense>
  )
}