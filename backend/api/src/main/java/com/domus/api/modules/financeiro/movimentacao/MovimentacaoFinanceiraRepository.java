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
        LEFT JOIN FETCH m.pessoa
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
        LEFT JOIN FETCH m.pessoa
        LEFT JOIN FETCH m.atualizadoPor ap
        LEFT JOIN FETCH ap.pessoa
        WHERE m.igreja.id = :igrejaId
          AND (:tipo IS NULL OR m.tipo = :tipo)
          AND (:categoriaId IS NULL OR c.id = :categoriaId)
          AND m.dataMovimentacao >= :dataInicio
          AND m.dataMovimentacao <= :dataFim
          AND (:q IS NULL OR LOWER(m.descricao) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY m.dataMovimentacao DESC, m.createdAt DESC
    """)
    Page<MovimentacaoFinanceira> buscarComFiltros(@Param("igrejaId") UUID igrejaId,
                                                  @Param("tipo") TipoMovimentacao tipo,
                                                  @Param("categoriaId") UUID categoriaId,
                                                  @Param("dataInicio") LocalDate dataInicio,
                                                  @Param("dataFim") LocalDate dataFim,
                                                  @Param("q") String q,
                                                  Pageable pageable);

    @Query("""
    SELECT m.id FROM MovimentacaoFinanceira m
    WHERE m.categoria.id = :categoriaId AND m.igreja.id = :igrejaId
""")
    List<UUID> buscarIdsPorCategoria(@Param("categoriaId") UUID categoriaId, @Param("igrejaId") UUID igrejaId);

    /** A11: quantos lançamentos usam a categoria — um COUNT só, sob demanda (ver Javadoc de
     *  {@code ContagemMovimentacoesResponse}), não durante a listagem de categorias. */
    long countByCategoriaIdAndIgrejaId(UUID categoriaId, UUID igrejaId);

    @Query("""
    SELECT m.id FROM MovimentacaoFinanceira m
    WHERE m.pessoa.id = :pessoaId AND m.igreja.id = :igrejaId
""")
    List<UUID> buscarIdsPorMembro(@Param("pessoaId") UUID pessoaId, @Param("igrejaId") UUID igrejaId);
}