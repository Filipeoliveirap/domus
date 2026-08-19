'use client'

import { Suspense, useState } from 'react'
import Link from 'next/link'
import { useRouter, useSearchParams } from 'next/navigation'
import { Pencil, Archive, ArrowDownCircle, ArrowUpCircle } from 'lucide-react'
import { useDebounce } from '@/hooks/useDebounce'
import { useMovimentacoes } from '@/hooks/financeiro/movimentacao/useMovimentacoes'
import { useMovimentacaoTotais } from '@/hooks/financeiro/movimentacao/useMovimentacaoTotais'
import { useCategoriasSelect } from '@/hooks/financeiro/categoria/useCategoriaSelect'
import { SelecaoPessoa } from '@/components/module/movimentacoes/SelecaoPessoa'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { DrawerDetalheMovimentacao } from '@/app/(app)/financeiro/movimentacoes/(lista)/(detalhe)/DrawerDetalheMovimentacao'
import { ModalArquivarMovimentacao } from './ModalArquivarMovimentacao'
import { formatarMoeda, formatarData, rotuloTipo, varianteTipo } from '@/lib/formats/financeiro/movimentacaoFormat'
import type { MovimentacaoResponse, TipoMovimentacao } from '@/types/financeiro/movimentacao.type'
import styles from './movimentacoes.module.css'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { usePaginaUrl } from '@/hooks/busca/usePaginaUrl'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { EstadoVazio } from "@/components/common/EstadoVazio/EstadoVazio";
import { SearchX, Inbox } from 'lucide-react'
import { SkeletonMovimentacoes } from "./SkeletonMovimentacoes";
import { CampoData } from '@/components/common/CampoData/CampoData'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

const TAMANHO_PAGINA = 15

function CabecalhoTabela() {
  return (
    <div className={styles.tabelaHeader}>
      <span className={styles.colDesc}>DESCRIÇÃO</span>
      <span className={styles.colCat}>CATEGORIA</span>
      <span className={styles.colData}>DATA</span>
      <span className={styles.colTipo}>TIPO</span>
      <span className={styles.colValor}>VALOR</span>
      <span className={styles.colAcoes}>AÇÕES</span>
    </div>
  )
}

function PainelCarregando() {
  return (
    <div className={styles.painel}>
      <CabecalhoTabela />
      <SkeletonMovimentacoes linhas={TAMANHO_PAGINA} />
    </div>
  )
}

function MovimentacoesConteudo() {
  const router = useRouter()
  const searchParams = useSearchParams()
  const detalheId = searchParams.get('detalhe')
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)

  const { filtros, setFiltro, setFiltros } = useFiltrosUrl({
    tipo: '',
    categoriaId: '',
    dataInicio: '',
    dataFim: '',
    q: '',
    pessoaId: '',
  })

  const { pagina, setPagina } = usePaginaUrl()
  const [movArquivando, setMovArquivando] = useState<MovimentacaoResponse | null>(null)
  // Nome só existe enquanto durar a navegação (a URL guarda o id, não o nome) — some num
  // refresh de página, o que é aceitável: o filtro continua aplicado, só perde o rótulo.
  const [pessoaFiltroNome, setPessoaFiltroNome] = useState<string | undefined>(undefined)

  const qDebounced = useDebounce(filtros.q, 250)

  const { data: categorias } = useCategoriasSelect(autorizado)
  const { data, isLoading, isError, refetch } = useMovimentacoes({
    tipo: (filtros.tipo as TipoMovimentacao) || undefined,
    categoriaId: filtros.categoriaId || undefined,
    dataInicio: filtros.dataInicio || undefined,
    dataFim: filtros.dataFim || undefined,
    q: qDebounced || undefined,
    pessoaId: filtros.pessoaId || undefined,
    page: pagina,
    size: TAMANHO_PAGINA,
  }, autorizado)

  const movimentacoes = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  const { data: totais } = useMovimentacaoTotais({
    tipo: (filtros.tipo as TipoMovimentacao) || undefined,
    categoriaId: filtros.categoriaId || undefined,
    dataInicio: filtros.dataInicio || undefined,
    dataFim: filtros.dataFim || undefined,
    q: qDebounced || undefined,
    pessoaId: filtros.pessoaId || undefined,
  }, autorizado)

  function resetarPagina() {
    setPagina(0)
  }

  function limparFiltros() {
    setFiltros({ tipo: '', categoriaId: '', dataInicio: '', dataFim: '', q: '', pessoaId: '' })
    setPessoaFiltroNome(undefined)
    setPagina(0)
  }

  const temFiltro = filtros.tipo || filtros.categoriaId || filtros.dataInicio || filtros.dataFim || filtros.q || filtros.pessoaId

  function abrirDetalhe(mov: MovimentacaoResponse) {
    router.push(`/financeiro/movimentacoes?detalhe=${mov.id}`, { scroll: false })
  }
  function fecharDetalhe() {
    router.push('/financeiro/movimentacoes', { scroll: false })
  }

  function acoesDaLinha(mov: MovimentacaoResponse): ItemAcao[] {
    return [
      { label: 'Editar', icone: Pencil, onClick: () => router.push(`/financeiro/movimentacoes/${mov.id}`) },
      { label: 'Arquivar', icone: Archive, onClick: () => setMovArquivando(mov), perigo: true, separadorAntes: true },
    ]
  }

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <PainelCarregando />
      </div>
    )
  }

  if (!autorizado) {
    return <AcessoRestrito />
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>Movimentações</h1>
            {totalElementos > 0 && <span className={styles.contador}>{totalElementos}</span>}
          </div>
          <p className={styles.subtitulo}>Entradas e saídas registradas.</p>
        </div>
        <Link href="/financeiro/movimentacoes/cadastrar" className={styles.botaoPrimario}>
          Nova movimentação
        </Link>
      </header>

      {/* Filtros */}
      <div className={styles.filtros}>
        <div className={styles.filtroCampo}>
          <label className={styles.filtroLabel}>TIPO</label>
          <select
            className={styles.filtroSelect}
            value={filtros.tipo}
            onChange={(e) => { setFiltro('tipo', e.target.value); resetarPagina() }}
          >
            <option value="">Todos os tipos</option>
            <option value="ENTRADA">Entrada</option>
            <option value="SAIDA">Saída</option>
          </select>
        </div>

        <div className={styles.filtroCampo}>
          <label className={styles.filtroLabel}>CATEGORIA</label>
          <select
            className={styles.filtroSelect}
            value={filtros.categoriaId}
            onChange={(e) => { setFiltro('categoriaId', e.target.value); resetarPagina() }}
          >
            <option value="">Todas as categorias</option>
            {categorias?.map((c: CategoriaResponse) => (
              <option key={c.id} value={c.id}>{c.nome}</option>
            ))}
          </select>
        </div>

        <div className={styles.filtroCampo}>
          <label className={styles.filtroLabel}>DE</label>
          <CampoData
            semLabel
            value={filtros.dataInicio}
            onChange={(v) => { setFiltro('dataInicio', v); resetarPagina() }}
          />
        </div>

        <div className={styles.filtroCampo}>
          <label className={styles.filtroLabel}>ATÉ</label>
          <CampoData
            semLabel
            value={filtros.dataFim}
            onChange={(v) => { setFiltro('dataFim', v); resetarPagina() }}
          />
        </div>

        <div className={styles.filtroCampo} style={{ flex: 1 }}>
          <label className={styles.filtroLabel}>BUSCAR</label>
          <input
            type="text"
            className={styles.filtroInput}
            placeholder="Buscar na descrição..."
            value={filtros.q}
            onChange={(e) => { setFiltro('q', e.target.value); resetarPagina() }}
          />
        </div>

        <div className={styles.filtroCampo} style={{ flex: 1 }}>
          <label className={styles.filtroLabel}>CONTRIBUINTE / BENEFICIÁRIO</label>
          <SelecaoPessoa
            pessoaIdSelecionado={filtros.pessoaId || undefined}
            nomeSelecionado={pessoaFiltroNome}
            label="pessoa"
            onSelecionar={(id, nome) => {
              setFiltro('pessoaId', id ?? '')
              setPessoaFiltroNome(nome)
              resetarPagina()
            }}
          />
        </div>

        {temFiltro && (
          <button className={styles.btnLimpar} onClick={limparFiltros}>Limpar</button>
        )}
      </div>

      {temFiltro && totais && (
        <div className={styles.totais}>
          <div className={styles.totalItem}>
            <span className={styles.totalLabel}>Entradas</span>
            <span className={`${styles.totalValor} ${styles.entrada}`}>{formatarMoeda(totais.totalEntradas)}</span>
          </div>
          <div className={styles.totalItem}>
            <span className={styles.totalLabel}>Saídas</span>
            <span className={`${styles.totalValor} ${styles.saida}`}>{formatarMoeda(totais.totalSaidas)}</span>
          </div>
          <div className={styles.totalItem}>
            <span className={styles.totalLabel}>Saldo</span>
            <span className={styles.totalValor}>
              {formatarMoeda(String(parseFloat(totais.totalEntradas) - parseFloat(totais.totalSaidas)))}
            </span>
          </div>
        </div>
      )}

      {/* Tabela */}
      <div className={styles.painel}>
        {isLoading ? (
          <>
            <CabecalhoTabela />
            <SkeletonMovimentacoes linhas={TAMANHO_PAGINA} />
          </>
        ) : isError ? (
          <EstadoErro
            titulo="Não foi possível carregar as movimentações"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : movimentacoes.length === 0 ? (
          <EstadoVazio
            icone={temFiltro ? SearchX : Inbox}
            titulo={temFiltro ? 'Nenhuma movimentação encontrada' : 'Nenhuma movimentação registrada'}
            mensagem={
              temFiltro
                ? 'Nenhuma movimentação corresponde aos filtros aplicados. Tente ajustá-los.'
                : 'Comece registrando a primeira entrada ou saída.'
            }
            acaoSecundaria={temFiltro ? { label: 'Limpar filtros', onClick: limparFiltros } : undefined}
            acaoPrimaria={!temFiltro ? { label: 'Nova movimentação', onClick: () => router.push('/financeiro/movimentacoes/cadastrar') } : undefined}
          />
        ) : (
          <>
            <CabecalhoTabela />

            <div className={styles.linhas}>
              {movimentacoes.map((mov) => (
                <div key={mov.id} className={styles.linha} onClick={() => abrirDetalhe(mov)}>
                  <div className={styles.colDesc}>
                    <span className={styles.descTexto}>{mov.descricao || '—'}</span>
                    {mov.contribuintes.length > 0 && (
                      <span className={styles.descMembro}>
                        {mov.contribuintes[0].pessoaNome}
                        {mov.contribuintes.length > 1 && ` +${mov.contribuintes.length - 1}`}
                      </span>
                    )}
                  </div>
                  <div className={styles.colCat}>{mov.categoriaNome}</div>
                  <div className={styles.colData}>{formatarData(mov.dataMovimentacao)}</div>
                  <div className={styles.colTipo}>
                    <span className={`${styles.selo} ${styles[varianteTipo(mov.tipo)]}`}>
                      {mov.tipo === 'ENTRADA' ? <ArrowDownCircle size={14} /> : <ArrowUpCircle size={14} />}
                      {rotuloTipo(mov.tipo)}
                    </span>
                  </div>
                  <div className={`${styles.colValor} ${styles[varianteTipo(mov.tipo)]}`}>
                    {mov.tipo === 'SAIDA' && '- '}{formatarMoeda(mov.valor)}
                  </div>
                  <div className={styles.colAcoes} onClick={(e) => e.stopPropagation()}>
                    <MenuAcoes itens={acoesDaLinha(mov)} />
                  </div>
                </div>
              ))}
            </div>

            <footer className={styles.rodape}>
              <span className={styles.contagem}>Exibindo {movimentacoes.length} de {totalElementos}</span>
              <div className={styles.paginacao}>
                <button onClick={() => setPagina((p) => Math.max(0, p - 1))} disabled={pagina === 0} className={styles.botaoPagina}>‹</button>
                <span className={styles.infoPagina}>{pagina + 1} de {totalPaginas}</span>
                <button onClick={() => setPagina((p) => p + 1)} disabled={pagina + 1 >= totalPaginas} className={styles.botaoPagina}>›</button>
              </div>
            </footer>
          </>
        )}
      </div>

      {detalheId && (
        <DrawerDetalheMovimentacao movimentacaoId={detalheId} onClose={fecharDetalhe} />
      )}
      {movArquivando && (
        <ModalArquivarMovimentacao movimentacao={movArquivando} onClose={() => setMovArquivando(null)} />
      )}
    </div>
  )
}

export default function MovimentacoesPage() {
  return (
    <Suspense fallback={<div>Carregando…</div>}>
      <MovimentacoesConteudo />
    </Suspense>
  )
}