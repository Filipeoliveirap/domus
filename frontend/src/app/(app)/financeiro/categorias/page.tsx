'use client'

import { useState, Suspense } from 'react'
import { ArrowDownCircle, ArrowUpCircle, ArrowLeftRight, Pencil, Archive, Trash2 } from 'lucide-react'
import { useCategorias } from '@/hooks/financeiro/categoria/useCategorias'
import { useExcluirCategoriaDefinitivamente } from '@/hooks/financeiro/categoria/useExcluirCategoriaDefinitivamente'
import { MenuAcoes, ItemAcao } from '@/components/common/menuacoes/MenuAcoes'
import { ModalCategoriaForm } from '@/app/(app)/financeiro/categorias/ModalCategoriaForm'
import { ModalArquivarCategoria } from '@/app/(app)/financeiro/categorias/ModalArquivarCategoria'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { rotuloTipoCategoria, varianteTipoCategoria } from '@/lib/formats/financeiro/categoriaFormat'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'
import styles from './categoria.module.css'
import { Transicao } from '@/components/common/Transicao/Transicao'
import { useBuscaUrl } from '@/hooks/busca/useBuscaUrl'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { EstadoVazio } from "@/components/common/EstadoVazio/EstadoVazio";
import { SearchX, Inbox } from 'lucide-react'
import { SkeletonCategorias } from "./SkeletonCategorias";
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'

const TAMANHO_PAGINA = 20

function IconeTipo({ tipo }: { tipo: CategoriaResponse['tipo'] }) {
  if (tipo === 'ENTRADA') return <ArrowDownCircle size={20} />
  if (tipo === 'SAIDA') return <ArrowUpCircle size={20} />
  return <ArrowLeftRight size={20} />
}

function CabecalhoTabela() {
  return (
    <div className={styles.tabelaHeader}>
      <span className={styles.colNome}>NOME</span>
      <span className={styles.colTipo}>TIPO</span>
      <span className={styles.colAcoes}>AÇÕES</span>
    </div>
  )
}

function PainelCarregando() {
  return (
    <div className={styles.painel}>
      <CabecalhoTabela />
      <SkeletonCategorias linhas={8} />
    </div>
  )
}

function CategoriasConteudo() {
  const { busca, setBusca, buscaDebounced } = useBuscaUrl({ delay: 250 })
  const [pagina, setPagina] = useState(0)
  const [modalForm, setModalForm] = useState<{ aberto: boolean; categoria?: CategoriaResponse }>({ aberto: false })
  const [categoriaArquivando, setCategoriaArquivando] = useState<CategoriaResponse | null>(null)
  const [categoriaExcluindo, setCategoriaExcluindo] = useState<CategoriaResponse | null>(null)
  const hidratado = useAuthStore((s) => s.hidratado)
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore(s => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)

  const { data, isLoading, isError, isFetching, refetch } = useCategorias({
    q: buscaDebounced,
    page: pagina,
    size: TAMANHO_PAGINA,
    enabled: autorizado,
  })

  const categorias = data?.content ?? []
  const totalPaginas = data?.totalPages ?? 0
  const totalElementos = data?.totalElements ?? 0

  function aoBuscar(valor: string) {
    setBusca(valor)
    setPagina(0)
  }

  function acoesDaLinha(categoria: CategoriaResponse): ItemAcao[] {
    // Sem nenhuma movimentação vinculada, arquivar (reversível, mas dá trabalho: exige
    // digitar o nome pra confirmar) é fricção desnecessária — apagar direto é seguro e só
    // pede uma confirmação simples (padrão "hard delete quando vazio").
    return [
      { label: 'Editar', icone: Pencil, onClick: () => setModalForm({ aberto: true, categoria }) },
      categoria.temVinculo
        ? { label: 'Arquivar', icone: Archive, onClick: () => setCategoriaArquivando(categoria), perigo: true, separadorAntes: true }
        : { label: 'Excluir', icone: Trash2, onClick: () => setCategoriaExcluindo(categoria), perigo: true, separadorAntes: true },
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

      {/* Remonta só ao trocar página; digitar na busca NÃO remonta — as linhas atualizam
          no lugar (cada nova entra com @starting-style) e a lista só amortece no fetch. */}
      <div className={styles.painel}>
       <Transicao key={pagina} modo="fade">
        {isLoading ? (
          <>
            <CabecalhoTabela />
            <SkeletonCategorias linhas={8} />
          </>
        ) : isError ? (
          <EstadoErro
            titulo="Não foi possível carregar as categorias"
            mensagem="Verifique sua conexão e tente novamente."
            aoTentarNovamente={() => refetch()}
          />
        ) : categorias.length === 0 ? (
          <EstadoVazio
            icone={buscaDebounced ? SearchX : Inbox}
            titulo={buscaDebounced ? 'Nenhuma categoria encontrada' : 'Nenhuma categoria cadastrada'}
            mensagem={
              buscaDebounced
                ? `Não encontramos resultados para "${buscaDebounced}". Tente outro termo.`
                : 'Comece criando a primeira categoria financeira.'
            }
            acaoSecundaria={buscaDebounced ? { label: 'Limpar busca', onClick: () => setBusca('') } : undefined}
            acaoPrimaria={!buscaDebounced ? { label: 'Nova categoria', onClick: () => setModalForm({ aberto: true }) } : undefined}
          />
        ) : (
          <>
            <CabecalhoTabela />

            <div className={`${styles.linhas} ${isFetching && !isLoading ? styles.listaAtualizando : ''}`}>
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
       </Transicao>
      </div>

      {modalForm.aberto && (
        <ModalCategoriaForm
          categoria={modalForm.categoria}
          onClose={() => setModalForm({ aberto: false })}
        />
      )}

      {categoriaArquivando && (
        <ModalArquivarCategoria
          categoria={categoriaArquivando}
          onClose={() => setCategoriaArquivando(null)}
        />
      )}

      {categoriaExcluindo && (
        <ModalExcluirCategoria
          categoria={categoriaExcluindo}
          onClose={() => setCategoriaExcluindo(null)}
        />
      )}
    </div>
  )
}

function ModalExcluirCategoria({ categoria, onClose }: { categoria: CategoriaResponse; onClose: () => void }) {
  const { confirmar, isLoading } = useExcluirCategoriaDefinitivamente(categoria, onClose)

  return (
    <ModalConfirmacao
      titulo="Excluir categoria?"
      mensagem={<>Isso vai apagar <strong>{categoria.nome}</strong> de vez. Não tem como desfazer.</>}
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}

export default function CategoriasPage() {
  return (
    <Suspense fallback={<div className={styles.pagina}>Carregando…</div>}>
      <CategoriasConteudo />
    </Suspense>
  )
}