'use client'

import { useState, Suspense } from 'react'
import { ArrowDownCircle, ArrowUpCircle, ArrowLeftRight, Pencil, Archive } from 'lucide-react'
import { useCategorias } from '@/hooks/financeiro/categoria/useCategorias'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { ModalCategoriaForm } from '@/app/(app)/financeiro/categorias/ModalCategoriaForm'
import { ModalArquivarCategoria } from '@/app/(app)/financeiro/categorias/ModalArquivarCategoria'
import { rotuloTipoCategoria, varianteTipoCategoria } from '@/lib/formats/financeiro/categoriaFormat'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'
import styles from './categoria.module.css'
import { useBuscaUrl } from '@/hooks/busca/useBuscaUrl'

const TAMANHO_PAGINA = 20

function IconeTipo({ tipo }: { tipo: CategoriaResponse['tipo'] }) {
  if (tipo === 'ENTRADA') return <ArrowDownCircle size={20} />
  if (tipo === 'SAIDA') return <ArrowUpCircle size={20} />
  return <ArrowLeftRight size={20} />
}

function CategoriasConteudo() {
  const { busca, setBusca, buscaDebounced } = useBuscaUrl({ delay: 250 })
  const [pagina, setPagina] = useState(0)
  const [modalForm, setModalForm] = useState<{ aberto: boolean; categoria?: CategoriaResponse }>({ aberto: false })
  const [categoriaArquivando, setCategoriaArquivando] = useState<CategoriaResponse | null>(null)

  const { data, isLoading, isError } = useCategorias({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
  })

  const categorias = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  function acoesDaLinha(categoria: CategoriaResponse): ItemAcao[] {
    return [
      { label: 'Editar', icone: Pencil, onClick: () => setModalForm({ aberto: true, categoria }) },
      { label: 'Arquivar', icone: Archive, onClick: () => setCategoriaArquivando(categoria), perigo: true, separadorAntes: true },
    ]
  }

  return (
    <div className={styles.pagina}>
      <header className={styles.cabecalho}>
        <div>
          <div className={styles.tituloLinha}>
            <h1 className={styles.titulo}>Categorias</h1>
            {totalElementos > 0 && (
              <span className={styles.contador}>{totalElementos} categorias</span>
            )}
          </div>
          <p className={styles.subtitulo}>Classificações das movimentações financeiras.</p>
        </div>
        <button className={styles.botaoPrimario} onClick={() => setModalForm({ aberto: true })}>
          Nova categoria
        </button>
      </header>

      <div className={styles.barraBusca}>
        <input
          type="text"
          value={busca}
          onChange={(e) => aoBuscar(e.target.value)}
          placeholder="Buscar categoria..."
          className={styles.inputBusca}
        />
      </div>

      <div className={styles.painel}>
        {isLoading ? (
          <div className={styles.linhas}>
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className={styles.skeleton} />
            ))}
          </div>
        ) : isError ? (
          <div className={styles.estadoErro}>Não foi possível carregar as categorias.</div>
        ) : categorias.length === 0 ? (
          <div className={styles.estadoVazio}>
            {buscaDebounced
              ? `Nenhuma categoria encontrada para "${buscaDebounced}".`
              : 'Nenhuma categoria cadastrada ainda.'}
          </div>
        ) : (
          <>
            {/* Cabeçalho da tabela */}
            <div className={styles.tabelaHeader}>
              <span className={styles.colNome}>NOME</span>
              <span className={styles.colTipo}>TIPO</span>
              <span className={styles.colAcoes}>AÇÕES</span>
            </div>

            <div className={styles.linhas}>
              {categorias.map((categoria) => (
                <div key={categoria.id} className={styles.linha}>
                  <div className={styles.colNome}>
                    <span className={`${styles.iconeBox} ${styles[varianteTipoCategoria(categoria.tipo)]}`}>
                      <IconeTipo tipo={categoria.tipo} />
                    </span>
                    <span className={styles.nome}>{categoria.nome}</span>
                  </div>
                  <div className={styles.colTipo}>
                    <span className={`${styles.selo} ${styles[varianteTipoCategoria(categoria.tipo)]}`}>
                      <span className={styles.seloDot} />
                      {rotuloTipoCategoria(categoria.tipo)}
                    </span>
                  </div>
                  <div className={styles.colAcoes}>
                    <MenuAcoes itens={acoesDaLinha(categoria)} />
                  </div>
                </div>
              ))}
            </div>

            <footer className={styles.rodape}>
              <span className={styles.contagem}>
                Exibindo {categorias.length} de {totalElementos}
              </span>
              <div className={styles.paginacao}>
                <button
                  onClick={() => setPagina((p) => Math.max(0, p - 1))}
                  disabled={pagina === 0}
                  className={styles.botaoPagina}
                >‹</button>
                <span className={styles.infoPagina}>{pagina + 1} de {totalPaginas}</span>
                <button
                  onClick={() => setPagina((p) => p + 1)}
                  disabled={pagina + 1 >= totalPaginas}
                  className={styles.botaoPagina}
                >›</button>
              </div>
            </footer>
          </>
        )}
      </div>

      {/* Modal criar/editar */}
      {modalForm.aberto && (
        <ModalCategoriaForm
          categoria={modalForm.categoria}
          onClose={() => setModalForm({ aberto: false })}
        />
      )}

      {/* Modal arquivar */}
      {categoriaArquivando && (
        <ModalArquivarCategoria
          categoria={categoriaArquivando}
          onClose={() => setCategoriaArquivando(null)}
        />
      )}
    </div>
  )
}

export default function CategoriasPage() {
  return (
    <Suspense fallback={<div className={styles.pagina}>Carregando…</div>}>
      <CategoriasConteudo />
    </Suspense>
  )
}