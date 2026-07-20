package com.domus.api.modules.evento;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoRepository extends JpaRepository<Evento, UUID> {

    Optional<Evento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    /**
     * Trava a LINHA do evento para serializar a contagem de vagas.
     *
     * <p>Sem isto, sob READ COMMITTED duas inscrições simultâneas na última vaga leem a
     * mesma contagem antiga e AMBAS passam. Mesma classe de erro do vínculo de igrejas (V14).
     *
     * <p>O lock é por evento, então inscrições em eventos diferentes não se bloqueiam.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Evento e WHERE e.id = :id AND e.igreja.id = :igrejaId")
    Optional<Evento> buscarComLock(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);

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