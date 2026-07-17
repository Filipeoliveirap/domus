package com.domus.api.modules.sync;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoDocument;
import com.domus.api.modules.financeiro.movimentacao.busca.MovimentacaoSearchRepository;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class MovimentacaoSincronizador implements SincronizadorEntidade {

    private final MovimentacaoFinanceiraRepository movimentacaoRepository;
    private final MovimentacaoSearchRepository movimentacaoSearchRepository;

    @Override
    public TipoEntidadeOutbox getTipoEntidade() {
        return TipoEntidadeOutbox.MOVIMENTACAO;
    }

    @Override
    public void indexar(UUID entidadeId) {
        Optional<MovimentacaoFinanceira> mov = movimentacaoRepository.findById(entidadeId);
        mov.ifPresentOrElse(
                m -> {
                    movimentacaoSearchRepository.save(MovimentacaoDocument.de(m));
                    log.debug("Movimentação indexada no Elastic. id={}", entidadeId);
                },
                () -> {
                    movimentacaoSearchRepository.deleteById(entidadeId.toString());
                    log.debug("Movimentação não encontrada no Postgres, removida do Elastic. id={}", entidadeId);
                }
        );
    }

    @Override
    public void remover(UUID entidadeId) {
        movimentacaoSearchRepository.deleteById(entidadeId.toString());
        log.debug("Movimentação removida do Elastic. id={}", entidadeId);
    }
}