package com.domus.api.modules.evento.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InscricaoRepository extends JpaRepository<InscricaoEvento, UUID> {

    /**
     * Vagas contam PESSOAS, não inscrições: cada inscrição confirmada vale 1 (o membro)
     * mais o número de acompanhantes que ele trouxe. Canceladas não contam.
     */
    @Query("""
        SELECT COALESCE(COUNT(i), 0) + COALESCE(
                   (SELECT COUNT(a) FROM AcompanhanteInscricao a
                     WHERE a.inscricao.evento.id = :eventoId
                       AND a.inscricao.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA), 0)
        FROM InscricaoEvento i
        WHERE i.evento.id = :eventoId
          AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
    """)
    long contarPessoasConfirmadas(@Param("eventoId") UUID eventoId);

    Optional<InscricaoEvento> findByEventoIdAndMembroId(UUID eventoId, UUID membroId);

    Optional<InscricaoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT DISTINCT i FROM InscricaoEvento i
        LEFT JOIN FETCH i.acompanhantes
        JOIN FETCH i.membro
        WHERE i.evento.id = :eventoId AND i.status = com.domus.api.modules.evento.inscricao.StatusInscricao.CONFIRMADA
        ORDER BY i.createdAt ASC
    """)
    List<InscricaoEvento> listarPorEvento(@Param("eventoId") UUID eventoId);

    List<InscricaoEvento> findByMembroIdAndStatus(UUID membroId, StatusInscricao status);
}
