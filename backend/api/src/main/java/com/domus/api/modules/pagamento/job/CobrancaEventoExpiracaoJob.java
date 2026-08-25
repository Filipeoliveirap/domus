package com.domus.api.modules.pagamento.job;

import com.domus.api.modules.pagamento.cobranca.CobrancaEventoRepository;
import com.domus.api.modules.pagamento.cobranca.StatusCobranca;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Expira cobrança de evento pago vencida, liberando a vaga. Roda a cada 5 minutos —
 * suficiente dado que o prazo mínimo de cobrança é 30 minutos. */
@Component
public class CobrancaEventoExpiracaoJob {

    private final CobrancaEventoRepository repository;

    public CobrancaEventoExpiracaoJob(CobrancaEventoRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void executar() {
        var vencidas = repository.findByStatusAndExpiraEmBefore(StatusCobranca.PENDENTE, Instant.now());
        if (vencidas.isEmpty()) return;

        vencidas.forEach(com.domus.api.modules.pagamento.cobranca.CobrancaEvento::marcarComoExpirado);
        repository.saveAll(vencidas);
    }
}
