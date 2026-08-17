package com.domus.api.modules.financeiro.movimentacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoFinanceiraRepository extends JpaRepository<MovimentacaoFinanceira, UUID> {

    /** Movimentações mais recentes (para o dashboard). Usar Pageable para limitar. */
    @Query("""
        SELECT m FROM MovimentacaoFinanceira m
        WHERE m.igreja.id = :igrejaId
        ORDER BY m.dataMovimentacao DESC, m.createdAt DESC
    """)
    List<MovimentacaoFinanceira> recentes(@Param("igrejaId") UUID igrejaId, Pageable pageable);

    @Query("""
        SELECT m FROM MovimentacaoFinanceira m
        JOIN FETCH m.categoria
        JOIN FETCH m.criadoPor cp
        JOIN FETCH cp.pessoa
        LEFT JOIN FETCH m.contribuintes ct
        LEFT JOIN FETCH ct.pessoa
        LEFT JOIN FETCH m.atualizadoPor ap
        LEFT JOIN FETCH ap.pessoa
        WHERE m.id = :id AND m.igreja.id = :igrejaId
    """)
    Optional<MovimentacaoFinanceira> buscarPorIdComRelacoes(@Param("id") UUID id,
                                                            @Param("igrejaId") UUID igrejaId);

    @Query("""
        SELECT m FROM MovimentacaoFinanceira m
        JOIN FETCH m.categoria c
        JOIN FETCH m.criadoPor cp
        JOIN FETCH cp.pessoa
        LEFT JOIN FETCH m.contribuintes ct
        LEFT JOIN FETCH ct.pessoa
        LEFT JOIN FETCH m.atualizadoPor ap
        LEFT JOIN FETCH ap.pessoa
        WHERE m.igreja.id = :igrejaId
          AND (:tipo IS NULL OR m.tipo = :tipo)
          AND (:categoriaId IS NULL OR c.id = :categoriaId)
          AND m.dataMovimentacao >= :dataInicio
          AND m.dataMovimentacao <= :dataFim
          AND (:q IS NULL OR LOWER(m.descricao) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
          AND (:pessoaId IS NULL OR EXISTS (
              SELECT 1 FROM MovimentacaoContribuinte ct2
              WHERE ct2.movimentacao = m AND ct2.pessoa.id = :pessoaId
          ))
        ORDER BY m.dataMovimentacao DESC, m.createdAt DESC
    """)
    Page<MovimentacaoFinanceira> buscarComFiltros(@Param("igrejaId") UUID igrejaId,
                                                  @Param("tipo") TipoMovimentacao tipo,
                                                  @Param("categoriaId") UUID categoriaId,
                                                  @Param("dataInicio") LocalDate dataInicio,
                                                  @Param("dataFim") LocalDate dataFim,
                                                  @Param("q") String q,
                                                  @Param("pessoaId") UUID pessoaId,
                                                  Pageable pageable);

    @Query("""
    SELECT m.id FROM MovimentacaoFinanceira m
    WHERE m.categoria.id = :categoriaId AND m.igreja.id = :igrejaId
""")
    List<UUID> buscarIdsPorCategoria(@Param("categoriaId") UUID categoriaId, @Param("igrejaId") UUID igrejaId);

    /**
     * A11: quantos lançamentos usam a categoria — um COUNT só, sob demanda (ver Javadoc de
     * {@code ContagemMovimentacoesResponse}), não durante a listagem de categorias.
     *
     * <p>Nativa de propósito: uma derived query aqui (`categoria.id = ...`) navega a
     * associação `categoria`, e o @SQLRestriction("deleted_at IS NULL") de CategoriaFinanceira
     * vaza pro JOIN implícito — categoria ARQUIVADA com movimentações reais contaria 0,
     * deixando excluirDefinitivo liberar um hard delete que deveria estar bloqueado (mesmo
     * bug já corrigido em CelulaMembroRepository/MinisterioMembroRepository).
     */
    @Query(value = """
        SELECT COUNT(*) FROM movimentacao_financeira
        WHERE categoria_id = :categoriaId AND igreja_id = :igrejaId AND deleted_at IS NULL
        """, nativeQuery = true)
    long countByCategoriaIdAndIgrejaId(@Param("categoriaId") UUID categoriaId, @Param("igrejaId") UUID igrejaId);

    @Query("""
    SELECT DISTINCT ct.movimentacao.id FROM MovimentacaoContribuinte ct
    WHERE ct.pessoa.id = :pessoaId AND ct.movimentacao.igreja.id = :igrejaId
""")
    List<UUID> buscarIdsPorMembro(@Param("pessoaId") UUID pessoaId, @Param("igrejaId") UUID igrejaId);

    /** Totais (não paginados) dos mesmos filtros de {@link #buscarComFiltros} — usado para
     *  mostrar a soma de entradas/saídas da lista filtrada, não só da página atual. */
    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN m.tipo = 'ENTRADA' THEN m.valor ELSE 0 END), 0) AS totalEntradas,
            COALESCE(SUM(CASE WHEN m.tipo = 'SAIDA' THEN m.valor ELSE 0 END), 0) AS totalSaidas,
            COUNT(m) AS quantidade
        FROM MovimentacaoFinanceira m
        JOIN m.categoria c
        WHERE m.igreja.id = :igrejaId
          AND (:tipo IS NULL OR m.tipo = :tipo)
          AND (:categoriaId IS NULL OR c.id = :categoriaId)
          AND m.dataMovimentacao >= :dataInicio
          AND m.dataMovimentacao <= :dataFim
          AND (:q IS NULL OR LOWER(m.descricao) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
          AND (:pessoaId IS NULL OR EXISTS (
              SELECT 1 FROM MovimentacaoContribuinte ct2
              WHERE ct2.movimentacao = m AND ct2.pessoa.id = :pessoaId
          ))
    """)
    TotaisAgregados agregarTotaisComFiltros(@Param("igrejaId") UUID igrejaId,
                                            @Param("tipo") TipoMovimentacao tipo,
                                            @Param("categoriaId") UUID categoriaId,
                                            @Param("dataInicio") LocalDate dataInicio,
                                            @Param("dataFim") LocalDate dataFim,
                                            @Param("q") String q,
                                            @Param("pessoaId") UUID pessoaId);

    interface TotaisAgregados {
        java.math.BigDecimal getTotalEntradas();
        java.math.BigDecimal getTotalSaidas();
        Long getQuantidade();
    }
}