package com.domus.api.modules.sync;

import com.domus.api.modules.membro.MembroRepository;
import com.domus.api.modules.membro.busca.MembroDocument;
import com.domus.api.modules.membro.busca.MembroSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MembroSincronizador implements SincronizadorEntidade {

    private final MembroRepository membroRepository;
    private final MembroSearchRepository membroSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.MEMBRO;
    }

    @Override
    public void indexar(UUID entidadeId) {
        membroRepository.findById(entidadeId).ifPresentOrElse(
                membro -> {
                    membroSearchRepository.save(MembroDocument.de(membro));
                    log.debug("Membro indexado no Elastic. id={}", entidadeId);
                },
                () -> {
                    membroSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Membro não encontrado no Postgres, removido do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        membroSearchRepository.deleteById(entidadeId.toString());
        log.debug("Membro removido do Elastic. id={}", entidadeId);
    }
}
