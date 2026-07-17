package com.domus.api.modules.financeiro.movimentacao.busca;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.outbox.TipoEventoOutbox;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReindexacaoMovimentacaoService {

    private final MovimentacaoFinanceiraRepository repository;
    private final OutboxRegistrador outboxRegistrador;

    @Transactional
    public void reindexarPorCategoria(UUID categoriaId, UUID igrejaId) {
        List<UUID> ids = repository.buscarIdsPorCategoria(categoriaId, igrejaId);
        if (ids.isEmpty()) return;
        log.info("Reindexando {} movimentações por alteração na categoria. categoria_id={}, igreja_id={}",
                ids.size(), categoriaId, igrejaId);
        ids.forEach(id -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.ATUALIZADO, id, igrejaId));
    }

    @Transactional
    public void reindexarPorMembro(UUID membroId, UUID igrejaId) {
        List<UUID> ids = repository.buscarIdsPorMembro(membroId, igrejaId);
        if (ids.isEmpty()) return;
        log.info("Reindexando {} movimentações por alteração no membro. membro_id={}, igreja_id={}",
                ids.size(), membroId, igrejaId);
        ids.forEach(id -> outboxRegistrador.registrar(
                TipoEntidadeOutbox.MOVIMENTACAO, TipoEventoOutbox.ATUALIZADO, id, igrejaId));
    }
}