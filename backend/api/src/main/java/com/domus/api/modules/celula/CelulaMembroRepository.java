package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaMembroRepository extends JpaRepository<CelulaMembro, UUID> {

    /**
     * Nativa de propósito: uma derived query aqui (`celula.id = ...`) navega a associação
     * `celula`, e o @SQLRestriction("deleted_at IS NULL") da entidade Celula vaza pro JOIN
     * implícito — os membros de uma célula ARQUIVADA somem da consulta mesmo existindo.
     * SQL nativo não sofre esse vazamento (a restrição só se aplica a JPQL/Criteria).
     */
    @Query(value = "SELECT * FROM celula_membro WHERE celula_id = :celulaId ORDER BY papel ASC", nativeQuery = true)
    List<CelulaMembro> findByCelulaIdOrderByPapelAsc(@Param("celulaId") UUID celulaId);

    Optional<CelulaMembro> findByPessoaId(UUID pessoaId);

    Optional<CelulaMembro> findByVisitanteId(UUID visitanteId);

    /** Nativa pelo mesmo motivo de findByCelulaIdOrderByPapelAsc. */
    @Query(value = "SELECT EXISTS(SELECT 1 FROM celula_membro WHERE celula_id = :celulaId)", nativeQuery = true)
    boolean existsByCelulaId(@Param("celulaId") UUID celulaId);

    /** Nativa pelo mesmo motivo de findByCelulaIdOrderByPapelAsc. */
    @Query(value = """
        SELECT EXISTS(
            SELECT 1 FROM celula_membro
            WHERE celula_id = :celulaId AND pessoa_id = :pessoaId AND papel = :papel
        )
        """, nativeQuery = true)
    boolean existsByCelulaIdAndPessoaIdAndPapel(@Param("celulaId") UUID celulaId,
                                                 @Param("pessoaId") UUID pessoaId,
                                                 @Param("papel") String papel);

    List<CelulaMembro> findByCelulaIdAndVisitanteIdIsNotNull(UUID celulaId);

    List<CelulaMembro> findByCelulaIdAndPessoaIdIsNotNull(UUID celulaId);

    /** Pra reindexação em lote: evita N+1 consultando visitante->célula um a um. */
    @Query("SELECT cm.visitante.id, cm.celula.id FROM CelulaMembro cm WHERE cm.visitante IS NOT NULL")
    List<Object[]> buscarCelulaIdPorVisitanteId();
}
