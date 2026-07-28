package com.domus.api.modules.celula;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CelulaRepository extends JpaRepository<Celula, UUID> {

    List<Celula> findByIgrejaIdOrderByNomeAsc(UUID igrejaId);

    Optional<Celula> findByIdAndIgrejaId(UUID id, UUID igrejaId);

    @Modifying
    @Query(value = "DELETE FROM celula WHERE id = :id", nativeQuery = true)
    void hardDeleteById(@Param("id") UUID id);
}
