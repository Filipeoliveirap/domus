package com.domus.api.modules.igreja.exclusao;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.*;

class PurgaIgrejaServiceTest {

    MovimentacaoFinanceiraRepository movimentacaoRepository;
    CategoriaFinanceiraRepository categoriaRepository;
    InscricaoRepository inscricaoRepository;
    PurgaIgrejaService service;
    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        inscricaoRepository = mock(InscricaoRepository.class);
        service = new PurgaIgrejaService(movimentacaoRepository, categoriaRepository, inscricaoRepository);
    }

    @Test
    void purgaApagaInscricoesMovimentacoesECategoriasNaOrdemCerta() {
        service.purgar(igrejaId);

        var ordem = inOrder(inscricaoRepository, movimentacaoRepository, categoriaRepository);
        ordem.verify(inscricaoRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(movimentacaoRepository).deleteAllByIgrejaId(igrejaId);
        ordem.verify(categoriaRepository).deleteAllByIgrejaId(igrejaId);
    }
}
