'use client'

import { useState, Suspense } from 'react'
import Link from 'next/link'
import { clsx } from 'clsx'
import { ChevronRight, Pencil, Trash2, Phone as PhoneIcon, Grid3x3 } from 'lucide-react'
import { useFecharAnimado } from '@/hooks/useFecharAnimado'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useVisitantes } from '@/hooks/visitante/useVisitantes'
import { useBuscaUrl } from '@/hooks/busca/useBuscaUrl'
import { usePaginaUrl } from '@/hooks/busca/usePaginaUrl'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { PainelFiltros, GrupoFiltro } from '@/components/common/PainelFiltros/PainelFiltros'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { useAuthStore } from '@/store/authStore'
import { useQueryClient } from '@tanstack/react-query'
import { notificar } from '@/components/common/Notificacao/notificar'
import { invalidarCache } from '@/lib/cacheInvalidacao'
import { visitanteService } from '@/services/visitante.service'
import { podeGerenciarVisitantes } from '@/lib/permissoes'
import { SkeletonVisitantes } from './SkeletonVisitantes'
import { DrawerDetalheVisitante } from './(detalhe)/DrawerDetalheVisitante'
import { ExcluirVisitante } from './(excluir)/ExcluirVisitante'
import { iniciaisVisitante, formatarTelefoneExibicao } from '@/lib/formats/visitanteFormat'
import { useCelulas } from '@/hooks/celula/useCelulas'
import { useRotulos } from '@/lib/rotulos/useRotulos'
import type { VisitanteResponse } from '@/types/visitante.type'
import { SearchX, Users } from 'lucide-react'
import styles from './page.module.css'

const TAMANHO_PAGINA = 10

const GRUPOS_FILTRO: GrupoFiltro[] = [
  {
    chave: 'contato',
    titulo: 'Contato',
    opcoes: [
      { valor: 'true', label: 'Feito' },
      { valor: 'false', label: 'Pendente' },
    ],
  },
  {
    chave: 'visita',
    titulo: 'Visita',
    opcoes: [
      { valor: 'true', label: 'Feito' },
      { valor: 'false', label: 'Pendente' },
    ],
  },
  {
    chave: 'acompanhamento',
    titulo: 'Acompanhamento',
    opcoes: [
      { valor: 'true', label: 'Feito' },
      { valor: 'false', label: 'Pendente' },
    ],
  },
]

function VisitantesConteudo() {
  const { busca, setBusca, buscaDebounced } = useBuscaUrl()
  const { filtros, setFiltros } = useFiltrosUrl({ contato: '', visita: '', acompanhamento: '' })
  const { pagina, setPagina } = usePaginaUrl()
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const podeGerenciar = podeGerenciarVisitantes(role, capacidadesExtras)
  const queryClient = useQueryClient()
  const { celula } = useRotulos()

  const [visitanteExcluindo, setVisitanteExcluindo] = useState<VisitanteResponse | null>(null)
  const [visitanteDetalheId, setVisitanteDetalheId] = useState<string | null>(null)
  const [visitanteMoverCelula, setVisitanteMoverCelula] = useState<VisitanteResponse | null>(null)
  const moverSaida = useFecharAnimado(() => setVisitanteMoverCelula(null), 240)

  const { data: celulas } = useCelulas()

  const contatoFiltro = filtros.contato === 'true' ? true : filtros.contato === 'false' ? false : undefined
  const visitaFiltro = filtros.visita === 'true' ? true : filtros.visita === 'false' ? false : undefined
  const acompanhamentoFiltro = filtros.acompanhamento === 'true' ? true : filtros.acompanhamento === 'false' ? false : undefined

  const temFiltrosAtivos = filtros.contato !== '' || filtros.visita !== '' || filtros.acompanhamento !== ''
  const temBuscaOuFiltro = buscaDebounced !== '' || temFiltrosAtivos

  async function handleMoverParaCelula(celulaId: string) {
    if (!visitanteMoverCelula) return
    try {
      await visitanteService.moverParaCelula(visitanteMoverCelula.id, celulaId)
      queryClient.invalidateQueries({ queryKey: ['visitantes'] })
      invalidarCache(queryClient, 'celula')
      notificar.sucesso(`${visitanteMoverCelula.nome} movido para ${celula.singular.toLowerCase()}.`)
      moverSaida.fechar()
    } catch { notificar.erro(`Erro ao mover para ${celula.singular.toLowerCase()}.`) }
  }

  function limparBuscaEFiltros() {
    setBusca('')
    setFiltros({ contato: '', visita: '', acompanhamento: '' })
  }

  const { data, isLoading, isError, isFetching, refetch } = useVisitantes({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
    contatoRealizado: contatoFiltro,
    visitaRealizada: visitaFiltro,
    acompanhamentoFeito: acompanhamentoFiltro,
  })

  function aoAplicarFiltros(valores: Record<string, string>) {
    setFiltros(valores as { contato: string; visita: string; acompanhamento: string })
    setPagina(0)
  }

  const visitantes = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  async function handleToggle(
    id: string,
    toggleFn: (id: string, data: { valor: boolean }) => Promise<VisitanteResponse>,
    current: boolean,
  ) {
    try {
      await toggleFn(id, { valor: !current })
      // Invalida lista E detalhe (drawer) — se o drawer desse visitante estiver aberto,
      // reflete o toggle na hora, sem precisar fechar e reabrir.
      invalidarCache(queryClient, 'visitante')
    } catch {
      notificar.erro('Erro ao atualizar. Tente novamente.')
    }
  }

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.containerTabela}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th>Visitante</th>
                <th>Telefone</th>
                <th>Status de Integração</th>
                <th className={styles.colunaAcoes}>Ações</th>
              </tr>
            </thead>
            <tbody>
              <SkeletonVisitantes linhas={TAMANHO_PAGINA} />
            </tbody>
          </table>
        </div>
      </div>
    )
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Visitantes</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>Visitantes</h1>
            {totalElementos > 0 && <span className={styles.contador}>{totalElementos}</span>}
          </div>
          <p className={styles.subtitulo}>Gerencie o acompanhamento e integração dos novos visitantes da comunidade.</p>
        </div>
        {podeGerenciar && (
          <Link href="/pessoas/visitantes/cadastrar" className={styles.botaoPrimario}>
            Novo visitante
          </Link>
        )}
      </header>

      <div className={styles.barraBusca}>
        <input
          type="text"
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar por nome ou telefone..."
          className={styles.inputBusca}
        />
        <PainelFiltros grupos={GRUPOS_FILTRO} valores={filtros} onAplicar={aoAplicarFiltros} />
      </div>

      <div className={styles.containerTabela}>
       {/* Remonta só ao trocar filtro/página; digitar na busca NÃO remonta — linhas
           atualizam no lugar e a tabela só amortece enquanto o fetch corre. */}
       <Transicao
         key={`${filtros.contato}|${filtros.visita}|${filtros.acompanhamento}|${pagina}`}
         modo="fade"
       >
        <table className={`${styles.tabela} ${isFetching && !isLoading ? styles.tabelaAtualizando : ''}`}>
          <thead>
            <tr>
              <th>Visitante</th>
              <th>Telefone</th>
              <th>Status de Integração</th>
              {podeGerenciar && <th className={styles.colunaAcoes}>Ações</th>}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <SkeletonVisitantes linhas={TAMANHO_PAGINA} />
            ) : isError ? (
              <tr><td colSpan={podeGerenciar ? 4 : 3}>
                <EstadoErro
                  titulo="Não foi possível carregar os visitantes"
                  mensagem="Verifique sua conexão e tente novamente."
                  aoTentarNovamente={() => refetch()}
                />
              </td></tr>
            ) : visitantes.length === 0 ? (
              <tr>
                <td colSpan={podeGerenciar ? 4 : 3}>
                  <EstadoVazio
                    icone={temBuscaOuFiltro ? SearchX : Users}
                    titulo={
                      temBuscaOuFiltro
                        ? `Nenhum visitante encontrado`
                        : 'Nenhum visitante cadastrado'
                    }
                    mensagem={
                      temBuscaOuFiltro
                        ? buscaDebounced
                          ? `Não encontramos resultados para "${buscaDebounced}".`
                          : 'Nenhum visitante corresponde aos filtros aplicados.'
                        : 'Comece cadastrando o primeiro visitante da sua igreja.'
                    }
                    acaoSecundaria={temBuscaOuFiltro ? { label: 'Limpar busca e filtros', onClick: limparBuscaEFiltros } : undefined}
                    acaoPrimaria={!temBuscaOuFiltro && podeGerenciar ? { label: 'Novo visitante', onClick: () => window.location.href = '/pessoas/visitantes/cadastrar' } : undefined}
                  />
                </td>
              </tr>
            ) : (
              visitantes.map((v) => {
                const acoes: ItemAcao[] = [
                  { label: 'Editar', icone: Pencil, onClick: () => window.location.href = `/pessoas/visitantes/${v.id}` },
                  { label: `Mover para ${celula.singular.toLowerCase()}`, icone: Grid3x3, onClick: () => setVisitanteMoverCelula(v) },
                  { label: 'Apagar', icone: Trash2, onClick: () => setVisitanteExcluindo(v), perigo: true, separadorAntes: true },
                ]

                return (
                  <tr key={v.id}
                    className={styles.linhaClicavel}
                    onClick={() => setVisitanteDetalheId(v.id)}
                  >
                    <td>
                      <div className={styles.celulaPessoa}>
                        <span className={styles.avatar}>
                          {iniciaisVisitante(v.nome)}
                        </span>
                        <span className={styles.nome}>{v.nome}</span>
                      </div>
                    </td>
                    <td className={styles.telefone}>
                      {v.telefone ? (
                        <span className={styles.telefoneComIcone}>
                          <PhoneIcon size={14} className={styles.phoneIconMobile} />
                          {formatarTelefoneExibicao(v.telefone)}
                        </span>
                      ) : '—'}
                    </td>
                    <td>
                      {podeGerenciar ? (
                        <div className={styles.toggles}>
                          <button
                            type="button"
                            className={`${styles.toggleBtn} ${v.contatoRealizado ? styles.toggleAtivo : styles.toggleInativo}`}
                            onClick={(e) => { e.stopPropagation(); handleToggle(v.id, visitanteService.toggleContato.bind(visitanteService), v.contatoRealizado) }}
                            title={`Contato: ${v.contatoRealizado ? 'Feito' : 'Pendente'}`}
                          >
                            Contato
                          </button>
                          <button
                            type="button"
                            className={`${styles.toggleBtn} ${v.visitaRealizada ? styles.toggleAtivo : styles.toggleInativo}`}
                            onClick={(e) => { e.stopPropagation(); handleToggle(v.id, visitanteService.toggleVisita.bind(visitanteService), v.visitaRealizada) }}
                            title={`Visita: ${v.visitaRealizada ? 'Feito' : 'Pendente'}`}
                          >
                            Visita
                          </button>
                          <button
                            type="button"
                            className={`${styles.toggleBtn} ${v.acompanhamentoFeito ? styles.toggleAtivo : styles.toggleInativo}`}
                            onClick={(e) => { e.stopPropagation(); handleToggle(v.id, visitanteService.toggleAcompanhamento.bind(visitanteService), v.acompanhamentoFeito) }}
                            title={`Acompanhamento: ${v.acompanhamentoFeito ? 'Feito' : 'Pendente'}`}
                          >
                            Acomp.
                          </button>
                        </div>
                      ) : (
                        <div className={styles.toggles}>
                          <span className={`${styles.toggleBtn} ${v.contatoRealizado ? styles.toggleAtivo : styles.toggleInativo}`}>Contato</span>
                          <span className={`${styles.toggleBtn} ${v.visitaRealizada ? styles.toggleAtivo : styles.toggleInativo}`}>Visita</span>
                          <span className={`${styles.toggleBtn} ${v.acompanhamentoFeito ? styles.toggleAtivo : styles.toggleInativo}`}>Acomp.</span>
                        </div>
                      )}
                    </td>
                    {podeGerenciar && (
                      <td className={styles.colunaAcoes} onClick={(e) => e.stopPropagation()}>
                        <MenuAcoes itens={acoes} />
                      </td>
                    )}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
       </Transicao>

        {!isLoading && !isError && visitantes.length > 0 && (
          <footer className={styles.rodape}>
            <span className={styles.contagem}>
              Exibindo {visitantes.length} de {totalElementos} visitantes
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
        )}
      </div>

      {visitanteExcluindo && (
        <ExcluirVisitante visitante={visitanteExcluindo} onClose={() => setVisitanteExcluindo(null)} />
      )}
      {visitanteDetalheId && (
        <DrawerDetalheVisitante visitanteId={visitanteDetalheId} onClose={() => setVisitanteDetalheId(null)} />
      )}
      {visitanteMoverCelula && (
        <div className={clsx(styles.modalOverlay, moverSaida.saindo && styles.modalSaindo)} onMouseDown={moverSaida.fechar}>
          <div className={styles.modalCelula} onMouseDown={e => e.stopPropagation()} role="dialog" aria-modal="true">
            <span className={styles.modalGrabber} aria-hidden="true" />
            <button className={styles.modalClose} onClick={moverSaida.fechar} aria-label="Fechar">✕</button>
            <h3 className={styles.modalTitulo}>Mover para {celula.singular.toLowerCase()}</h3>
            <p className={styles.modalSub}>{visitanteMoverCelula.nome}</p>
            <div className={styles.modalLista}>
              {celulas?.map(c => (
                <button key={c.id} className={styles.modalItem}
                  onClick={() => handleMoverParaCelula(c.id)}>
                  <span>{c.nome}</span>
                  <span className={styles.modalItemInfo}>{c.totalMembros} membros</span>
                </button>
              ))}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default function VisitantesPage() {
  return (
    <Suspense fallback={<div className={styles.pagina}>Carregando…</div>}>
      <VisitantesConteudo />
    </Suspense>
  )
}
