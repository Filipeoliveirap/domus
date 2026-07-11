'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { ChevronRight } from 'lucide-react'
import { useEventos } from '@/hooks/evento/useEventos'
import { useAuthStore } from '@/store/authStore'
import { EventoCard } from '@/components/module/eventos/EventoCard'
import { DrawerDetalheEvento } from '@/app/(app)/eventos/(detalhe)/DrawerDetalheEvento'
import { ModalArquivarEvento } from './ModalArquivarEvento'
import { EventoResponse } from '@/types/evento.type'
import { useBuscaUrl } from '@/hooks/busca/useBuscaUrl'
import styles from './Page.module.css'

const TAMANHO_PAGINA = 12

function EventosConteudo() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const detalheId = searchParams.get('detalhe')
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = role === 'ADMIN_IGREJA' || role === 'LIDER'

  const [pagina, setPagina] = useState(0)
  const { busca, setBusca, buscaDebounced } = useBuscaUrl({ delay: 250 })

  const [eventoArquivando, setEventoArquivando] = useState<EventoResponse | null>(null)

  const { data, isLoading, isError, isFetching } = useEventos({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
  })

  const eventos = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  function abrirDetalhe(evento: EventoResponse) {
    router.push(`/eventos?detalhe=${evento.id}`, { scroll: false })
  }
  function fecharDetalhe() {
    router.push('/eventos', { scroll: false })
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Eventos</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>
            {totalElementos > 0 ? `${totalElementos} eventos` : 'Eventos'}
          </h1>
          <p className={styles.subtitulo}>Agenda da igreja</p>
        </div>
        {podeGerenciar && (
          <Link href="/eventos/cadastrar" className={styles.botaoPrimario}>
            Novo evento
          </Link>
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
        {isFetching && <span className={styles.indicadorAtualizando}>Atualizando…</span>}
      </div>

      {isLoading ? (
        <div className={styles.grid}>
          {Array.from({ length: 8 }).map((_, i) => (
            <div key={i} className={styles.skeleton} />
          ))}
        </div>
      ) : isError ? (
        <div className={styles.estadoErro}>
          Não foi possível carregar os eventos. Tente novamente.
        </div>
      ) : eventos.length === 0 ? (
        <div className={styles.estadoVazio}>
          {buscaDebounced
            ? `Nenhum evento encontrado para "${buscaDebounced}".`
            : 'Nenhum evento cadastrado ainda.'}
        </div>
      ) : (
        <>
          <div className={styles.grid}>
            {eventos.map((evento) => (
              <EventoCard
                key={evento.id}
                evento={evento}
                onAbrirDetalhe={abrirDetalhe}
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

      {/* Drawer de detalhe*/}
      {detalheId && (
        <DrawerDetalheEvento eventoId={detalheId} onClose={fecharDetalhe} />
      )}

      {/* Modal de arquivar */}
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