package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaRepository extends JpaRepository<Celula, UUID> {

    List<Celula> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    Optional<Celula> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
