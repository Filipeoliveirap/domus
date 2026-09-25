'use client'

import { useState } from 'react'
import type { PagedResponse } from '@/types/pagedResponse.type'
import type { ContaResponse } from '@/types/contas-a-pagar'
import { ContasAPagarItem } from './ContasAPagarItem'
import styles from './ContasAPagarTabela.module.css'

interface Props {
  data?: PagedResponse<ContaResponse>
  carregando?: boolean
  destaqueId?: string | null
  onEditar: (conta: ContaResponse) => void
  onExcluir: (conta: ContaResponse) => void
  onPagar: (conta: ContaResponse) => void
  onBaixarRestante: (conta: ContaResponse) => void
  onPageChange: (page: number) => void
}

export function ContasAPagarTabela({
  data,
  carregando,
  destaqueId,
  onEditar,
  onExcluir,
  onPagar,
  onBaixarRestante,
  onPageChange,
}: Props) {
  const [expandedId, setExpandedId] = useState<string | null>(null)

  function toggleExpand(id: string) {
    setExpandedId((prev) => (prev === id ? null : id))
  }

  const totalPages = data?.totalPages ?? 1
  const currentPage = data?.pageNumber ?? 0

  if (carregando && !data) {
    return (
      <div className={styles.estado}>
        <div className={styles.esqueleto} />
        <div className={styles.esqueleto} />
        <div className={styles.esqueleto} />
      </div>
    )
  }

  if (!carregando && (!data || data.content.length === 0)) {
    return (
      <div className={styles.vazio}>
        <span className={styles.vazioTexto}>Nenhuma conta encontrada.</span>
      </div>
    )
  }

  return (
    <div className={styles.tabelaWrapper}>
      {/* Cabeçalho da tabela (desktop) */}
      <div className={styles.thead}>
        <div className={styles.thStatus} aria-label="Status" />
        <div className={styles.thDesc}>Descrição</div>
        <div className={styles.thCat}>Categoria</div>
        <div className={styles.thValor}>Valor</div>
        <div className={styles.thVen}>Vencimento</div>
        <div className={styles.thSit}>Situação</div>
        <div className={styles.thAcoes}>Ações</div>
      </div>

      {/* Items */}
      <div className={styles.tbody}>
        {carregando
          ? Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className={styles.linhaEsqueleto} />
            ))
          : data?.content.map((conta) => (
              <ContasAPagarItem
                key={conta.id}
                conta={conta}
                destaqueId={destaqueId}
                onEditar={onEditar}
                onExcluir={onExcluir}
                onPagar={onPagar}
                onBaixarRestante={onBaixarRestante}
                expandedId={expandedId}
                onToggle={toggleExpand}
              />
            ))}
      </div>

      {/* Paginação */}
      {totalPages > 1 && (
        <div className={styles.paginacao}>
          <button
            className={styles.botaoPagina}
            onClick={() => onPageChange(currentPage - 1)}
            disabled={currentPage === 0}
            aria-label="Página anterior"
          >
            ‹
          </button>
          <span className={styles.infoPagina}>
            {currentPage + 1} de {totalPages}
          </span>
          <button
            className={styles.botaoPagina}
            onClick={() => onPageChange(currentPage + 1)}
            disabled={currentPage + 1 >= totalPages}
            aria-label="Próxima página"
          >
            ›
          </button>
        </div>
      )}
    </div>
  )
}
