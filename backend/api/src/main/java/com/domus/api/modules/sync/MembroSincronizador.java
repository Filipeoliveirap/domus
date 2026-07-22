package com.domus.api.modules.sync;

import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.busca.PessoaDocument;
import com.domus.api.modules.pessoa.busca.PessoaSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MembroSincronizador implements SincronizadorEntidade {

    private final PessoaRepository pessoaRepository;
    private final PessoaSearchRepository pessoaSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.PESSOA;
    }

    @Override
    public void indexar(UUID entidadeId) {
        pessoaRepository.findById(entidadeId).ifPresentOrElse(
                pessoa -> {
                    pessoaSearchRepository.save(PessoaDocument.de(pessoa));
                    log.debug("Pessoa indexada no Elastic. id={}", entidadeId);
                },
                () -> {
                    pessoaSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Pessoa não encontrada no Postgres, removida do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        pessoaSearchRepository.deleteById(entidadeId.toString());
        log.debug("Pessoa removida do Elastic. id={}", entidadeId);
    }
}
