package com.domus.api.modules.financeiro.movimentacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    // Sem FETCH em m.categoria de propósito: JOIN eager herda o @SQLRestriction da categoria
    // e some com a movimentação inteira quando ela está arquivada. Nome resolvido à parte.
    @Query("""
        SELECT m FROM MovimentacaoFinanceira m
        LEFT JOIN FETCH m.criadoPor cp
        LEFT JOIN FETCH cp.pessoa
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
        LEFT JOIN m.categoria c
        LEFT JOIN FETCH m.criadoPor cp
        LEFT JOIN FETCH cp.pessoa
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

    /** Nativa: uma derived query herdaria o @SQLRestriction de CategoriaFinanceira e contaria 0 pra categoria arquivada, liberando hard delete indevido. */
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
        LEFT JOIN m.categoria c
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

    @Query(value = """
        SELECT * FROM movimentacao_financeira
        WHERE igreja_id = :igrejaId AND deleted_at IS NOT NULL
        ORDER BY data_movimentacao DESC
        """, nativeQuery = true)
    List<MovimentacaoFinanceira> findArquivadasPorIgreja(@Param("igrejaId") UUID igrejaId);

    /** Igual a {@link #findById}, mas enxerga arquivadas também — usado pela tela de Arquivadas. */
    @Query(value = "SELECT * FROM movimentacao_financeira WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    Optional<MovimentacaoFinanceira> findByIdAndIgrejaIdIncluindoArquivadas(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** Contribuintes desta movimentação — não tem @SQLRestriction próprio, mas a contagem
     *  entra no aviso de "excluir definitivamente" (eles somem junto, ON DELETE CASCADE). */
    @Query(value = "SELECT COUNT(*) FROM movimentacao_contribuinte WHERE movimentacao_id = :id", nativeQuery = true)
    long contarContribuintes(@Param("id") UUID id);

    @Modifying
    @Query(value = "UPDATE movimentacao_financeira SET deleted_at = NULL WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int restaurarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    /** UPDATE nativo, não {@code repository.delete(entidade)}: {@code contribuintes} tem
     *  cascade=ALL+orphanRemoval (necessário pro fluxo de editar) — deletar a entidade
     *  cascadeia um REMOVE de verdade nos filhos, apagando contribuintes na hora em vez de só
     *  arquivar a movimentação (que devia ser reversível). */
    @Modifying
    @Query(value = "UPDATE movimentacao_financeira SET deleted_at = NOW() WHERE id = :id AND igreja_id = :igrejaId", nativeQuery = true)
    int arquivarPorId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM movimentacao_financeira WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);

    @Modifying
    @Query(value = """
        UPDATE movimentacao_financeira
           SET criado_por_texto = CASE WHEN criado_por_usuario_id = :usuarioId THEN :nome ELSE criado_por_texto END,
               criado_por_usuario_id = CASE WHEN criado_por_usuario_id = :usuarioId THEN NULL ELSE criado_por_usuario_id END,
               atualizado_por_texto = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN :nome ELSE atualizado_por_texto END,
               atualizado_por_usuario_id = CASE WHEN atualizado_por_usuario_id = :usuarioId THEN NULL ELSE atualizado_por_usuario_id END
         WHERE criado_por_usuario_id = :usuarioId OR atualizado_por_usuario_id = :usuarioId
        """, nativeQuery = true)
    int desvincularUsuario(@Param("usuarioId") UUID usuarioId, @Param("nome") String nome);

    long countByIgrejaId(UUID igrejaId);

    /** Purga da igreja: movimentacao_contribuinte cascadeia sozinho via ON DELETE CASCADE. */
    @Modifying
    @Query(value = "DELETE FROM movimentacao_financeira WHERE igreja_id = :igrejaId", nativeQuery = true)
    void deleteAllByIgrejaId(@Param("igrejaId") UUID igrejaId);
}