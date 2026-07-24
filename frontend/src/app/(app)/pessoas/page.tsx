'use client'

import { useState, Suspense } from 'react'
import Link from 'next/link'
import { ChevronRight, Pencil, KeyRound, Archive } from 'lucide-react'
import { usePessoas } from '@/hooks/pessoa/usePessoas'
import { useBuscaUrl } from '@/hooks/busca/useBuscaUrl'
import { useFiltrosUrl } from '@/hooks/busca/useFiltrosUrl'
import { PainelFiltros, GrupoFiltro } from '@/components/common/PainelFiltros/PainelFiltros'
import type { Vinculo } from '@/types/pessoa.type'
import {
  iniciais, rotuloVinculo, varianteVinculo, formatarData, formatarTelefoneExibicao,
} from '@/lib/formats/pessoaFormat'
import { urlFoto } from '@/lib/urlFoto'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { PessoaResponse } from '@/types/pessoa.type'
import styles from './page.module.css'
import { ModalConcederAcesso } from './ModalConcederAcesso'
import { useRouter } from 'next/navigation'
import { ModalArquivarPessoa } from './(arquivar)/ArquivarPessoa'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { SearchX, Inbox } from 'lucide-react'
import { useAuthStore } from '@/store/authStore'
import { SkeletonPessoas } from "./SkeletonPessoas";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { DrawerDetalhePessoa } from './(detalhe)/DrawerDetalhePessoa'
import { podeGerenciarPessoas } from '@/lib/permissoes'

const TAMANHO_PAGINA = 10

const GRUPOS_FILTRO: GrupoFiltro[] = [
  {
    chave: 'vinculo',
    titulo: 'Vínculo',
    opcoes: [
      { valor: 'MEMBRO', label: 'Membros' },
      { valor: 'CONGREGANTE', label: 'Congregantes' },
    ],
  },
]

function PessoasConteudo() {
  const router = useRouter()
  const { busca, setBusca, buscaDebounced } = useBuscaUrl()
  const { filtros, setFiltros } = useFiltrosUrl({ vinculo: '' })
  const [pagina, setPagina] = useState(0)
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const podeGerenciar = podeGerenciarPessoas(role)

  const [pessoaConcedendo, setPessoaConcedendo] = useState<PessoaResponse | null>(null)
  const [pessoaArquivando, setPessoaArquivando] = useState<PessoaResponse | null>(null)
  const [pessoaDetalheId, setPessoaDetalheId] = useState<string | null>(null)

  const { data, isLoading, isError, isFetching, refetch } = usePessoas({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
    vinculo: filtros.vinculo as Vinculo | '',
  })

  function aoAplicarFiltros(valores: Record<string, string>) {
    setFiltros(valores as { vinculo: string })
    setPagina(0)
  }

  const pessoas = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  if (!hidratado) {
    return (
      <div className={styles.pagina}>
        <div className={styles.containerTabela}>
          <table className={styles.tabela}>
            <thead>
              <tr>
                <th>Pessoa</th>
                <th>Telefone</th>
                <th>Vínculo</th>
                <th>Cadastro</th>
              </tr>
            </thead>
            <tbody>
              <SkeletonPessoas linhas={TAMANHO_PAGINA} podeGerenciar={false} />
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
        <span className={styles.breadcrumbAtual}>Pessoas</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>
            {totalElementos > 0 ? `${totalElementos} pessoas` : 'Pessoas'}
          </h1>
          <p className={styles.subtitulo}>Pessoas registradas na igreja</p>
        </div>
        {podeGerenciar && (
          <Link href="/pessoas/cadastrar" className={styles.botaoPrimario}>
            Nova pessoa
          </Link>
        )}
      </header>

      <div className={styles.barraBusca}>
        <input
          type="text"
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar por nome, cargo ou e-mail..."
          className={styles.inputBusca}
        />
        <PainelFiltros grupos={GRUPOS_FILTRO} valores={filtros} onAplicar={aoAplicarFiltros} />
        {isFetching && <span className={styles.indicadorAtualizando}>Atualizando…</span>}
      </div>

      <div className={styles.containerTabela}>
        <table className={styles.tabela}>
          <thead>
            <tr>
              <th>Pessoa</th>
              <th>Telefone</th>
              <th>Vínculo</th>
              <th>Cadastro</th>
              {podeGerenciar && <th className={styles.colunaAcoes}>Ações</th>}
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <SkeletonPessoas linhas={TAMANHO_PAGINA} podeGerenciar={podeGerenciar} />
            ) : isError ? (
              <tr><td colSpan={podeGerenciar ? 5 : 4}>
                <EstadoErro
                  titulo="Não foi possível carregar as pessoas"
                  mensagem="Verifique sua conexão e tente novamente."
                  aoTentarNovamente={() => refetch()}
                />
              </td></tr>
            ) : pessoas.length === 0 ? (
              <tr>
                <td colSpan={podeGerenciar ? 5 : 4}>
                  <EstadoVazio
                    icone={buscaDebounced ? SearchX : Inbox}
                    titulo={buscaDebounced ? `Nenhuma pessoa encontrada` : 'Nenhuma pessoa cadastrada'}
                    mensagem={
                      buscaDebounced
                        ? `Não encontramos resultados para "${buscaDebounced}". Tente outro termo.`
                        : 'Comece cadastrando a primeira pessoa da sua igreja.'
                    }
                    acaoSecundaria={buscaDebounced ? { label: 'Limpar busca', onClick: () => setBusca('') } : undefined}
                    acaoPrimaria={!buscaDebounced && podeGerenciar ? { label: 'Nova pessoa', onClick: () => router.push('/pessoas/cadastrar') } : undefined}
                  />
                </td>
              </tr>
            ) : (
              pessoas.map((p) => {
                const acoes: ItemAcao[] = [
                  { label: 'Editar', icone: Pencil, onClick: () => router.push(`/pessoas/${p.id}`) },
                  { label: 'Convidar ao sistema', icone: KeyRound, onClick: () => setPessoaConcedendo(p) },
                  { label: 'Arquivar', icone: Archive, onClick: () => setPessoaArquivando(p), perigo: true, separadorAntes: true },
                ]

                return (
                  <tr key={p.id}
                    className={styles.linhaClicavel}
                    onClick={() => setPessoaDetalheId(p.id)}
                  >
                    <td>
                      <div className={styles.celulaPessoa}>
                        <span className={styles.avatar}>
                          {urlFoto(p.fotoId, 'THUMB') ? (
                            // eslint-disable-next-line @next/next/no-img-element -- servida por /api/fotos
                            <img src={urlFoto(p.fotoId, 'THUMB')!} alt="" className={styles.avatarFoto} />
                          ) : (
                            iniciais(p.nome)
                          )}
                        </span>
                        <div className={styles.pessoaInfo}>
                          <span className={styles.nome}>{p.nome}</span>
                          {p.cargo && <span className={styles.email}>{p.cargo}</span>}
                        </div>
                      </div>
                    </td>
                    <td className={styles.telefone}>
                      {formatarTelefoneExibicao(p.telefone)}
                    </td>
                    <td>
                      <span className={`${styles.statusBadge} ${styles[varianteVinculo(p.vinculo)]}`}>
                        {rotuloVinculo(p.vinculo)}
                      </span>
                    </td>
                    <td className={styles.cadastro}>
                      {formatarData(p.createdAt)}
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

        {!isLoading && !isError && pessoas.length > 0 && (
          <footer className={styles.rodape}>
            <span className={styles.contagem}>
              Exibindo {pessoas.length} de {totalElementos} pessoas
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

      {pessoaConcedendo && (
        <ModalConcederAcesso pessoa={pessoaConcedendo} onClose={() => setPessoaConcedendo(null)} />
      )}
      {pessoaArquivando && (
        <ModalArquivarPessoa pessoa={pessoaArquivando} onClose={() => setPessoaArquivando(null)} />
      )}
      {pessoaDetalheId && (
        <DrawerDetalhePessoa pessoaId={pessoaDetalheId} onClose={() => setPessoaDetalheId(null)} />
      )}
    </div>
  )
}

export default function PessoasPage() {
  return (
    <Suspense fallback={<div className={styles.pagina}>Carregando…</div>}>
      <PessoasConteudo />
    </Suspense>
  )
}
