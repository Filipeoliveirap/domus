package com.domus.api.modules.contapagar;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PagamentoContaRepository extends JpaRepository<PagamentoConta, UUID> {

    /** Busca pagamento por id + igreja (via conta.igrejaId). */
    @Query("SELECT p FROM PagamentoConta p WHERE p.id = :id AND p.conta.igreja.id = :igrejaId")
    Optional<PagamentoConta> findByIdAndIgrejaId(@Param("id") UUID id, @Param("igrejaId") UUID igrejaId);
}
