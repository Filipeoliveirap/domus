package com.domus.api.modules.evento.inscricao;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AcompanhanteRepository extends JpaRepository<AcompanhanteInscricao, UUID> {
}
