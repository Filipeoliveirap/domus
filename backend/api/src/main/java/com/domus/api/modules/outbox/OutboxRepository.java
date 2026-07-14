package com.domus.api.modules.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvento, UUID> {

    @Query("""
        SELECT o FROM OutboxEvento o
        WHERE o.processado = false
          AND o.tentativas < 5
        ORDER BY o.createdAt ASC
    """)
    List<OutboxEvento> buscarPendentes(Pageable pageable);
}