package com.domus.api.modules.sync;

import com.domus.api.modules.ministerio.MinisterioRepository;
import com.domus.api.modules.ministerio.busca.MinisterioDocument;
import com.domus.api.modules.ministerio.busca.MinisterioSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinisterioSincronizador implements SincronizadorEntidade {

    private final MinisterioRepository ministerioRepository;
    private final MinisterioSearchRepository ministerioSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.MINISTERIO;
    }

    @Override
    public void indexar(UUID entidadeId) {
        ministerioRepository.findById(entidadeId).ifPresentOrElse(
                ministerio -> {
                    ministerioSearchRepository.save(MinisterioDocument.de(ministerio));
                    log.debug("Ministério indexado no Elastic. id={}", entidadeId);
                },
                () -> {
                    ministerioSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Ministério não encontrado no Postgres, removido do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        ministerioSearchRepository.deleteById(entidadeId.toString());
        log.debug("Ministério removido do Elastic. id={}", entidadeId);
    }
}
