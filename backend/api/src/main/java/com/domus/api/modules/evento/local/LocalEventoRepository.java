package com.domus.api.modules.evento.local;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LocalEventoRepository extends JpaRepository<LocalEvento, UUID> {

    List<LocalEvento> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    /** Isolamento multi-tenant: NUNCA busque por id sozinho. */
    Optional<LocalEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    boolean existsByIgrejaIdAndNomeIgnoreCase(UUID igrejaId, String nome);
}
