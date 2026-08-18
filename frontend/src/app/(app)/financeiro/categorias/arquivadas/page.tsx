'use client'

import { useState } from 'react'
import { Archive, RotateCcw, Trash2, ArrowDownCircle, ArrowUpCircle, ArrowLeftRight } from 'lucide-react'
import { useCategoriasArquivadas } from '@/hooks/financeiro/categoria/useCategoriasArquivadas'
import { useContagemMovimentacoesCategoria } from '@/hooks/financeiro/categoria/useContagemMovimentacoesCategoria'
import { useRestaurarCategoria } from '@/hooks/financeiro/categoria/useRestaurarCategoria'
import { useExcluirCategoriaDefinitivamente } from '@/hooks/financeiro/categoria/useExcluirCategoriaDefinitivamente'
import { ModalConfirmacao } from '@/components/common/ModalConfirmacao/ModalConfirmacao'
import { EstadoVazio } from '@/components/common/EstadoVazio/EstadoVazio'
import { EstadoErro } from '@/components/common/EstadoErro/EstadoErro'
import { Skeleton } from '@/components/common/Skeleton/Skeleton'
import { AcessoRestrito } from '@/components/common/AcessoRestrito/AcessoRestrito'
import { useAuthStore } from '@/store/authStore'
import { podeVerFinanceiro } from '@/lib/permissoes'
import { rotuloTipoCategoria, varianteTipoCategoria } from '@/lib/formats/financeiro/categoriaFormat'
import type { CategoriaResponse } from '@/types/financeiro/categoria.type'
import styles from './arquivadas.module.css'

function IconeTipo({ tipo }: { tipo: CategoriaResponse['tipo'] }) {
  if (tipo === 'ENTRADA') return <ArrowDownCircle size={18} />
  if (tipo === 'SAIDA') return <ArrowUpCircle size={18} />
  return <ArrowLeftRight size={18} />
}

export default function CategoriasArquivadasPage() {
  const { data: categorias, isLoading, isError, refetch } = useCategoriasArquivadas()
  const role = useAuthStore((s) => s.role)
  const capacidadesExtras = useAuthStore((s) => s.capacidadesExtras)
  const autorizado = podeVerFinanceiro(role, capacidadesExtras)
  const { restaurar, isLoading: restaurando } = useRestaurarCategoria()
  const [excluindo, setExcluindo] = useState<CategoriaResponse | null>(null)

  if (!autorizado) {
    return <AcessoRestrito />
  }

  if (isLoading) {
    return (
      <div className={styles.lista}>
        {[1, 2].map((i) => <Skeleton key={i} width="100%" height="64px" radius="var(--radius-lg)" />)}
      </div>
    )
  }

  if (isError) {
    return <EstadoErro titulo="Erro ao carregar" mensagem="Verifique sua conexão." aoTentarNovamente={() => refetch()} />
  }

  if (!categorias || categorias.length === 0) {
    return <EstadoVazio icone={Archive} titulo="Nenhuma categoria arquivada" mensagem="Categorias arquivadas aparecem aqui." />
  }

  return (
    <>
      <div className={styles.lista}>
        {categorias.map((c) => (
          <div key={c.id} className={styles.linha}>
            <div className={styles.info}>
              <span className={`${styles.iconeBox} ${styles[varianteTipoCategoria(c.tipo)]}`}>
                <IconeTipo tipo={c.tipo} />
              </span>
              <div>
                <p className={styles.nome}>{c.nome}</p>
                <p className={styles.detalhe}>{rotuloTipoCategoria(c.tipo)}</p>
              </div>
            </div>
            <div className={styles.acoes}>
              <button
                className={styles.botaoRestaurar}
                disabled={restaurando}
                onClick={() => restaurar(c.id, c.nome)}
              >
                <RotateCcw size={14} /> Restaurar
              </button>
              {c.temVinculo ? (
                <BadgeBloqueada categoriaId={c.id} />
              ) : (
                <button className={styles.botaoExcluir} onClick={() => setExcluindo(c)}>
                  <Trash2 size={14} /> Excluir definitivamente
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      {excluindo && (
        <ModalExcluirDefinitivo categoria={excluindo} onClose={() => setExcluindo(null)} />
      )}
    </>
  )
}

/** Conta as movimentações vinculadas pra deixar claro POR QUÊ está bloqueado e o que fazer
 *  a respeito — em vez do texto genérico "tem movimentações vinculadas". */
function BadgeBloqueada({ categoriaId }: { categoriaId: string }) {
  const { data: total, isLoading } = useContagemMovimentacoesCategoria(categoriaId)

  return (
    <span className={styles.bloqueado}>
      <Trash2 size={14} />
      <span>
        {isLoading ? (
          'Verificando movimentações vinculadas…'
        ) : (
          <>
            Não pode ser excluída — {total} {total === 1 ? 'movimentação usa' : 'movimentações usam'} esta
            categoria. Pra excluir de vez, mude a categoria dessas movimentações (ou exclua-as
            definitivamente) primeiro.
          </>
        )}
      </span>
    </span>
  )
}

function ModalExcluirDefinitivo({ categoria, onClose }: { categoria: CategoriaResponse; onClose: () => void }) {
  const { confirmar, isLoading } = useExcluirCategoriaDefinitivamente(categoria, onClose)

  return (
    <ModalConfirmacao
      titulo="Excluir categoria definitivamente?"
      mensagem={<>Isso vai apagar <strong>{categoria.nome}</strong> de vez. Não tem como desfazer.</>}
      textoConfirmar="Excluir"
      perigo
      isLoading={isLoading}
      onConfirmar={confirmar}
      onClose={onClose}
    />
  )
}
