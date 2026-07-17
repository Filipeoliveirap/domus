package com.domus.api.modules.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxRegistrador {

    private final OutboxRepository outboxRepository;

    public void registrar(TipoEntidadeOutbox entidade, TipoEventoOutbox evento, UUID entidadeId, UUID igrejaId) {
        OutboxEvento outboxEvento = OutboxEvento.builder()
                .tipoEntidade(entidade)
                .tipoEvento(evento)
                .entidadeId(entidadeId)
                .igrejaId(igrejaId)
                .processado(false)
                .tentativas(0)
                .build();
        outboxRepository.save(outboxEvento);
    }
}
