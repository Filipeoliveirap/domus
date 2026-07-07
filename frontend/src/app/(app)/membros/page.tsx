'use client'

import { useState } from 'react'
import Link from 'next/link'
import { ChevronRight, Pencil, KeyRound, Archive } from 'lucide-react'
import { useDebounce } from '@/hooks/useDebounce'
import { useMembros } from '@/hooks/membro/useMembros'
import {
  iniciais,
  rotuloStatus,
  varianteStatus,
  formatarData,
  formatarTelefoneExibicao,
} from '@/lib/formats/membroFormat'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { MembroResponse } from '@/types/membro.type'
import styles from './page.module.css'
import { ModalConcederAcesso } from './ModalConcederAcesso'
import { useRouter } from 'next/navigation'
import { ModalArquivarMembro } from './(arquivar)/ArquivarMembro'

const TAMANHO_PAGINA = 10

export default function MembrosPage() {
  const [busca, setBusca] = useState('')
  const [pagina, setPagina] = useState(0)
  const buscaDebounced = useDebounce(busca, 350)
  const router = useRouter()

  // estados dos modais (conceder acesso / arquivar) — plugamos os modais depois
  const [membroConcedendo, setMembroConcedendo] = useState<MembroResponse | null>(null)
  const [membroArquivando, setMembroArquivando] = useState<MembroResponse | null>(null)

  const { data, isLoading, isError, isFetching } = useMembros({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
  })

  const membros = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  return (
    <div className={styles.pagina}>
      <nav className={styles.breadcrumb} aria-label="breadcrumb">
        <Link href="/inicio" className={styles.breadcrumbLink}>Início</Link>
        <ChevronRight size={16} className={styles.breadcrumbSep} />
        <span className={styles.breadcrumbAtual}>Membros</span>
      </nav>

      <header className={styles.cabecalho}>
        <div>
          <h1 className={styles.titulo}>
            {totalElementos > 0 ? `${totalElementos} membros` : 'Membros'}
          </h1>
          <p className={styles.subtitulo}>Pessoas registradas na igreja</p>
        </div>
        <Link href="/membros/cadastrar" className={styles.botaoPrimario}>
          Novo membro
        </Link>
      </header>

      <div className={styles.barraBusca}>
        <input
          type="text"
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar por nome ou e-mail..."
          className={styles.inputBusca}
        />
        {isFetching && <span className={styles.indicadorAtualizando}>Atualizando…</span>}
      </div>

      <div className={styles.containerTabela}>
        <table className={styles.tabela}>
          <thead>
            <tr>
              <th>Membro</th>
              <th>Telefone</th>
              <th>Status</th>
              <th>Cadastro</th>
              <th className={styles.colunaAcoes}>Ações</th>
            </tr>
          </thead>
          <tbody>
            {isLoading ? (
              <tr><td colSpan={5} className={styles.estadoVazio}>Carregando…</td></tr>
            ) : isError ? (
              <tr><td colSpan={5} className={styles.estadoErro}>
                Não foi possível carregar os membros. Tente novamente.
              </td></tr>
            ) : membros.length === 0 ? (
              <tr><td colSpan={5} className={styles.estadoVazio}>
                {buscaDebounced
                  ? `Nenhum membro encontrado para "${buscaDebounced}".`
                  : 'Nenhum membro cadastrado ainda.'}
              </td></tr>
            ) : (
              membros.map((m) => {
                const acoes: ItemAcao[] = [
                  { label: 'Editar', icone: Pencil, onClick: () => router.push(`/membros/${m.id}`) },
                  { label: 'Conceder acesso', icone: KeyRound, onClick: () => setMembroConcedendo(m) },
                  { label: 'Arquivar', icone: Archive, onClick: () => setMembroArquivando(m), perigo: true, separadorAntes: true },
                ]

                return (
                  <tr key={m.id}
                    className={styles.linhaClicavel}
                    onClick={() => router.push(`/membros/${m.id}`)}
                  >
                    <td>
                      <div className={styles.celulaMembro}>
                        <span className={styles.avatar}>{iniciais(m.nome)}</span>
                        <div className={styles.membroInfo}>
                          <span className={styles.nome}>{m.nome}</span>
                          {m.email && <span className={styles.email}>{m.email}</span>}
                        </div>
                      </div>
                    </td>
                    <td className={styles.telefone}>
                      {formatarTelefoneExibicao(m.telefone)}
                    </td>
                    <td>
                      <span className={`${styles.statusBadge} ${styles[varianteStatus(m.status)]}`}>
                        {rotuloStatus(m.status)}
                      </span>
                    </td>
                    <td className={styles.cadastro}>
                      {formatarData(m.createdAt)}
                    </td>
                    <td className={styles.colunaAcoes} onClick={(e) => e.stopPropagation()}>
                      <MenuAcoes itens={acoes} />
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>

        {!isLoading && !isError && membros.length > 0 && (
          <footer className={styles.rodape}>
            <span className={styles.contagem}>
              Exibindo {membros.length} de {totalElementos} membros
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

      {membroConcedendo && (
        <ModalConcederAcesso membro={membroConcedendo} onClose={() => setMembroConcedendo(null)} />
      )}
      {membroArquivando && (
        <ModalArquivarMembro membro={membroArquivando} onClose={() => setMembroArquivando(null)} />
      )}
    </div>
  )
}