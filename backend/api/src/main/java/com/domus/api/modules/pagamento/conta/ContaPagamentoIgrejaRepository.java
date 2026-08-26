package com.domus.api.modules.pagamento.conta;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaPagamentoIgrejaRepository extends JpaRepository<ContaPagamentoIgreja, UUID> {
    Optional<ContaPagamentoIgreja> findByIgrejaId(UUID igrejaId);
    Optional<ContaPagamentoIgreja> findByMpUserId(String mpUserId);
    void deleteByIgrejaId(UUID igrejaId);

    /** Usado por {@code MercadoPagoTokenRenovacaoJob} — contas cujo token vence antes de
     *  {@code limite} (hoje + margem de segurança) precisam renovar. */
    List<ContaPagamentoIgreja> findByExpiraEmBefore(Instant limite);
}
