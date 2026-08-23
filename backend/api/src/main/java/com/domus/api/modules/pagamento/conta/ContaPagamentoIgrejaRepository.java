package com.domus.api.modules.pagamento.conta;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaPagamentoIgrejaRepository extends JpaRepository<ContaPagamentoIgreja, UUID> {
    Optional<ContaPagamentoIgreja> findByIgrejaId(UUID igrejaId);
    void deleteByIgrejaId(UUID igrejaId);
}
