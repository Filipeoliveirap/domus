package com.domus.api.modules.outbox;

import com.domus.api.modules.sync.SincronizadorEntidade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class OutboxProcessador {

    private final OutboxRepository outboxRepository;
    private final Map<TipoEntidadeOutbox, SincronizadorEntidade> sincronizadores = new EnumMap<>(TipoEntidadeOutbox.class);

    public OutboxProcessador(OutboxRepository outboxRepository, List<SincronizadorEntidade> lista) {
        this.outboxRepository = outboxRepository;
        for (SincronizadorEntidade s : lista) {
            sincronizadores.put(s.getTipoEntidade(), s);
        }
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void processar() {
        List<OutboxEvento> pendentes = outboxRepository.buscarPendentes(PageRequest.of(0, 100));
        if (pendentes.isEmpty()) return;

        log.debug("Processando {} eventos do outbox.", pendentes.size());

        for (OutboxEvento evento : pendentes) {
            try {
                SincronizadorEntidade sync = sincronizadores.get(evento.getTipoEntidade());
                if (sync == null) {
                    log.warn("Sem sincronizador para entidade {}. Evento id={}", evento.getTipoEntidade(), evento.getId());
                    continue;
                }

                switch (evento.getTipoEvento()) {
                    case CRIADO, ATUALIZADO -> sync.indexar(evento.getEntidadeId());
                    case REMOVIDO -> sync.remover(evento.getEntidadeId());
                }

                evento.setProcessado(true);
                evento.setProcessadoAt(LocalDateTime.now());

            } catch (Exception e) {
                evento.setTentativas(evento.getTentativas() + 1);
                evento.setErro(e.getMessage());
                log.warn("Falha ao processar evento outbox. id={}, tentativa={}, erro={}",
                        evento.getId(), evento.getTentativas(), e.getMessage());
            }
        }

        outboxRepository.saveAll(pendentes);
    }
}
