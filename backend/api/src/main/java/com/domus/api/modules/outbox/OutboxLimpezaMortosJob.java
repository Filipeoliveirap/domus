package com.domus.api.modules.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Alerta diariamente sobre eventos outbox presos (tentativas >= 5).
 *
 * <p>O processador principal ({@link OutboxProcessador}) para de tentar depois de 5
 * falhas — não é dead-letter automático, mas fica esquecido na tabela. Este job existe
 * para que ninguém descubra meses depois que algo parou de sincronizar.
 *
 * <p>Rodar às 03:00 evita coincidir com o backup Postgres (06:00) e o ciclo de uso
 * normal. Não deleta — a decisão fica com o operador via SQL manual
 * (DELETE WHERE processado=false AND tentativas>=5) ou reset de tentativas para reprocessar.
 */
@Component
@Slf4j
public class OutboxLimpezaMortosJob {

    private final OutboxRepository outboxRepository;

    public OutboxLimpezaMortosJob(OutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional(readOnly = true)
    public void relatarMortos() {
        List<OutboxEvento> mortos = outboxRepository.buscarMortos();
        if (mortos.isEmpty()) {
            log.debug("Nenhum evento outbox preso.");
            return;
        }

        String ids = mortos.stream()
                .map(o -> o.getId().toString())
                .collect(Collectors.joining(","));
        String tipos = mortos.stream()
                .map(o -> o.getTipoEntidade().name())
                .distinct()
                .collect(Collectors.joining(","));
        String erros = mortos.stream()
                .map(OutboxEvento::getErro)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(" | "));

        log.warn("Outbox tem {} evento(s) preso(s) (tentativas >= 5). ids={}, tipos={}, erros={}",
                mortos.size(), ids, tipos, erros);
    }
}
