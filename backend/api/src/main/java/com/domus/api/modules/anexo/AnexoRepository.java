package com.domus.api.modules.anexo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AnexoRepository extends JpaRepository<Anexo, UUID> {

    Optional<Anexo> findByIdAndIgrejaId(UUID id, UUID igrejaId);
}
