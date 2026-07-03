package com.domus.api.modules.evento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, UUID> {

    Optional<Evento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id = :igrejaId
          AND (:q IS NULL OR LOWER(e.titulo) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%')))
        ORDER BY e.inicioEm ASC
    """)
    Page<Evento> buscarPorIgreja(@Param("igrejaId") UUID igrejaId,
                                 @Param("q") String q,
                                 Pageable pageable);
}