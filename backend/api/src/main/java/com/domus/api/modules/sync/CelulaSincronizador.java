package com.domus.api.modules.sync;

import com.domus.api.modules.celula.CelulaRepository;
import com.domus.api.modules.celula.busca.CelulaDocument;
import com.domus.api.modules.celula.busca.CelulaSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CelulaSincronizador implements SincronizadorEntidade {

    private final CelulaRepository celulaRepository;
    private final CelulaSearchRepository celulaSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.CELULA;
    }

    @Override
    public void indexar(UUID entidadeId) {
        celulaRepository.findById(entidadeId).ifPresentOrElse(
                celula -> {
                    celulaSearchRepository.save(CelulaDocument.de(celula));
                    log.debug("Célula indexada no Elastic. id={}", entidadeId);
                },
                () -> {
                    celulaSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Célula não encontrada no Postgres, removida do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        celulaSearchRepository.deleteById(entidadeId.toString());
        log.debug("Célula removida do Elastic. id={}", entidadeId);
    }
}
