package com.domus.api.modules.ministerio;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MinisterioMembroRepository extends JpaRepository<MinisterioMembro, UUID> {

    Optional<MinisterioMembro> findByMinisterioIdAndPessoaId(UUID ministerioId, UUID pessoaId);

    /**
     * Nativa de propósito: uma derived query aqui (`ministerio.id = ...`) navega a
     * associação `ministerio`, e o @SQLRestriction("deleted_at IS NULL") da entidade
     * Ministerio vaza pro JOIN implícito — os membros de um ministério ARQUIVADO somem
     * da consulta mesmo existindo (mesmo bug já corrigido em CelulaMembroRepository).
     */
    @Query(value = "SELECT * FROM ministerio_membro WHERE ministerio_id = :ministerioId ORDER BY papel ASC", nativeQuery = true)
    List<MinisterioMembro> findByMinisterioIdOrderByPapelAsc(@Param("ministerioId") UUID ministerioId);

    List<MinisterioMembro> findByPessoaIdAndIgrejaIdAndStatus(UUID pessoaId, UUID igrejaId, StatusMembro status);

    /** Nativa pelo mesmo motivo de findByMinisterioIdOrderByPapelAsc. */
    @Query(value = """
        SELECT EXISTS(
            SELECT 1 FROM ministerio_membro
            WHERE ministerio_id = :ministerioId AND pessoa_id = :pessoaId
              AND papel = :papel AND status = :status
        )
        """, nativeQuery = true)
    boolean existsByMinisterioIdAndPessoaIdAndPapelAndStatus(
            @Param("ministerioId") UUID ministerioId, @Param("pessoaId") UUID pessoaId,
            @Param("papel") String papel, @Param("status") String status);
}
