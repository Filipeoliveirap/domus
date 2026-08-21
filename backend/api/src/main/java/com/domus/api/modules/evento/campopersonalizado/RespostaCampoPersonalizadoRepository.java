package com.domus.api.modules.evento.campopersonalizado;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RespostaCampoPersonalizadoRepository extends JpaRepository<RespostaCampoPersonalizado, UUID> {

    List<RespostaCampoPersonalizado> findByInscricaoId(UUID inscricaoId);

    Optional<RespostaCampoPersonalizado> findByCampoIdAndInscricaoIdAndAcompanhanteId(
            UUID campoId, UUID inscricaoId, UUID acompanhanteId);
}
