package com.domus.api.modules.sync;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaDocument;
import com.domus.api.modules.financeiro.categoria.busca.CategoriaSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CategoriaSincronizador implements SincronizadorEntidade {

    private final CategoriaFinanceiraRepository categoriaRepository;
    private final CategoriaSearchRepository categoriaSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.CATEGORIA;
    }

    @Override
    public void indexar(UUID entidadeId) {
        categoriaRepository.findById(entidadeId).ifPresentOrElse(
                categoria -> {
                    categoriaSearchRepository.save(CategoriaDocument.de(categoria));
                    log.debug("Categoria indexada no Elastic. id={}", entidadeId);
                },
                () -> {
                    categoriaSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Categoria não encontrada no Postgres, removida do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        categoriaSearchRepository.deleteById(entidadeId.toString());
        log.debug("Categoria removida do Elastic. id={}", entidadeId);
    }
}