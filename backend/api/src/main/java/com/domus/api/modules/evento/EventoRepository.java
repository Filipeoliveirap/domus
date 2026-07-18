package com.domus.api.modules.evento;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, UUID> {

    Optional<Evento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /** Próximos eventos (a partir de agora), ordenados. Usar Pageable para limitar. */
    @Query("""
        SELECT e FROM Evento e
        WHERE e.igreja.id = :igrejaId AND e.inicioEm >= :agora
        ORDER BY e.inicioEm ASC
    """)
    List<Evento> proximos(@Param("igrejaId") UUID igrejaId, @Param("agora") LocalDateTime agora, Pageable pageable);

    /** Contagem de eventos com início dentro de um intervalo (mês / semana). */
    long countByIgrejaIdAndInicioEmBetween(UUID igrejaId, LocalDateTime de, LocalDateTime ate);

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