package com.domus.api.modules.pagamento.cobranca;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CobrancaEventoRepository extends JpaRepository<CobrancaEvento, UUID> {

    Optional<CobrancaEvento> findByTokenLinkPublico(String token);

    List<CobrancaEvento> findByInscricaoId(UUID inscricaoId);

    @Query("""
        SELECT COUNT(c) FROM CobrancaEvento c
        WHERE c.eventoId = :eventoId
        AND (c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PAGO
             OR (c.status = com.domus.api.modules.pagamento.cobranca.StatusCobranca.PENDENTE
                 AND c.expiraEm > :agora))
        """)
    long contarPessoasComVagaReservada(@Param("eventoId") UUID eventoId, @Param("agora") Instant agora);

    List<CobrancaEvento> findByStatusAndExpiraEmBefore(StatusCobranca status, Instant momento);
}
