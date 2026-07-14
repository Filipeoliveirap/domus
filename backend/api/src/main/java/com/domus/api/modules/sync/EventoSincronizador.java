package com.domus.api.modules.sync;

import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.busca.EventoDocument;
import com.domus.api.modules.evento.busca.EventoSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventoSincronizador implements SincronizadorEntidade {

    private final EventoRepository eventoRepository;
    private final EventoSearchRepository eventoSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.EVENTO;
    }

    @Override
    public void indexar(UUID entidadeId) {
        eventoRepository.findById(entidadeId).ifPresentOrElse(
                evento -> {
                    eventoSearchRepository.save(EventoDocument.de(evento));
                    log.debug("Evento indexado no Elastic. id={}", entidadeId);
                },
                () -> {
                    eventoSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Evento não encontrado no Postgres, removido do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        eventoSearchRepository.deleteById(entidadeId.toString());
        log.debug("Evento removido do Elastic. id={}", entidadeId);
    }
}