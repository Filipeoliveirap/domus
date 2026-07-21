package com.domus.api.modules.financeiro.categoria;

import com.domus.api.config.redis.CacheEvictor;
import com.domus.api.modules.financeiro.categoria.DTOs.ContagemMovimentacoesResponse;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.busca.ReindexacaoMovimentacaoService;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.outbox.OutboxRegistrador;
import com.domus.api.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A11: contagem de lançamentos por categoria, consultada sob demanda (não na listagem) pra o
 * front decidir se pede confirmação antes de salvar uma edição.
 */
class CategoriaFinanceiraServiceTest {

    CategoriaFinanceiraRepository repository;
    IgrejaRepository igrejaRepository;
    CacheEvictor cacheEvictor;
    ReindexacaoMovimentacaoService reindexacaoMovimentacaoService;
    OutboxRegistrador outboxRegistrador;
    MovimentacaoFinanceiraRepository movimentacaoFinanceiraRepository;
    CategoriaFinanceiraService service;

    final UUID igrejaId = UUID.randomUUID();
    final UUID categoriaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(CategoriaFinanceiraRepository.class);
        igrejaRepository = mock(IgrejaRepository.class);
        cacheEvictor = mock(CacheEvictor.class);
        reindexacaoMovimentacaoService = mock(ReindexacaoMovimentacaoService.class);
        outboxRegistrador = mock(OutboxRegistrador.class);
        movimentacaoFinanceiraRepository = mock(MovimentacaoFinanceiraRepository.class);
        service = new CategoriaFinanceiraService(repository, igrejaRepository, cacheEvictor,
                reindexacaoMovimentacaoService, outboxRegistrador, movimentacaoFinanceiraRepository);
    }

    private CategoriaFinanceira categoria() {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        return CategoriaFinanceira.builder()
                .id(categoriaId).igreja(igreja).nome("Dízimo").tipo(TipoCategoria.ENTRADA)
                .build();
    }

    @Test
    void contarMovimentacoesRetornaZeroQuandoCategoriaSemUso() {
        when(repository.findByIdAndIgrejaId(categoriaId, igrejaId)).thenReturn(Optional.of(categoria()));
        when(movimentacaoFinanceiraRepository.countByCategoriaIdAndIgrejaId(categoriaId, igrejaId))
                .thenReturn(0L);

        ContagemMovimentacoesResponse r = service.contarMovimentacoes(categoriaId, igrejaId);

        assertThat(r.total()).isZero();
    }

    @Test
    void contarMovimentacoesRetornaQuantidadeQuandoCategoriaEmUso() {
        when(repository.findByIdAndIgrejaId(categoriaId, igrejaId)).thenReturn(Optional.of(categoria()));
        when(movimentacaoFinanceiraRepository.countByCategoriaIdAndIgrejaId(categoriaId, igrejaId))
                .thenReturn(7L);

        ContagemMovimentacoesResponse r = service.contarMovimentacoes(categoriaId, igrejaId);

        assertThat(r.total()).isEqualTo(7L);
    }

    @Test
    void contarMovimentacoesDeCategoriaDeOutraIgrejaEh404() {
        when(repository.findByIdAndIgrejaId(categoriaId, igrejaId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.contarMovimentacoes(categoriaId, igrejaId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
