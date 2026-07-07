package com.domus.api.modules.financeiro.movimentacao;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface MovimentacaoFinanceiraRepository extends JpaRepository<MovimentacaoFinanceira, UUID> {

    @Query("""
        SELECT m FROM MovimentacaoFinanceira m
        JOIN FETCH m.categoria
        JOIN FETCH m.criadoPor cp
        JOIN FETCH cp.membro
        LEFT JOIN FETCH m.membro
        LEFT JOIN FETCH m.atualizadoPor ap
        LEFT JOIN FETCH ap.membro
        WHERE m.id = :id AND m.igreja.id = :igrejaId
    """)
    Optional<MovimentacaoFinanceira> buscarPorIdComRelacoes(@Param("id") UUID id,
                                                            @Param("igrejaId") UUID igrejaId);

    @Query("""
        SELECT m FROM MovimentacaoFinanceira m
        JOIN FETCH m.categoria c
        JOIN FETCH m.criadoPor cp
        JOIN FETCH cp.membro
        LEFT JOIN FETCH m.membro
        LEFT JOIN FETCH m.atualizadoPor ap
        LEFT JOIN FETCH ap.membro
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
}