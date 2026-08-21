package com.domus.api.modules.evento.campopersonalizado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CampoPersonalizadoEventoRepository extends JpaRepository<CampoPersonalizadoEvento, UUID> {

    List<CampoPersonalizadoEvento> findByEventoIdAndIgrejaIdOrderByOrdemAsc(UUID eventoId, UUID igrejaId);

    Optional<CampoPersonalizadoEvento> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
