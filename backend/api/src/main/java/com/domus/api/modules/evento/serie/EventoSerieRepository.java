package com.domus.api.modules.evento.serie;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventoSerieRepository extends JpaRepository<EventoSerie, UUID> {
    Optional<EventoSerie> findByIdAndIgrejaId(UUID id, UUID igrejaId);
    List<EventoSerie> findByIgrejaIdAndAtivaTrue(UUID igrejaId);
}
