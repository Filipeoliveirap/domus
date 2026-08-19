package com.domus.api.shared.busca;

import com.domus.api.modules.celula.busca.BuscaCelulaService;
import com.domus.api.modules.evento.busca.BuscaEventoService;
import com.domus.api.modules.financeiro.categoria.busca.BuscaCategoriaService;
import com.domus.api.modules.financeiro.movimentacao.busca.BuscaMovimentacaoService;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.ministerio.busca.BuscaMinisterioService;
import com.domus.api.modules.outbox.TipoEntidadeOutbox;
import com.domus.api.modules.pessoa.busca.BuscaPessoaService;
import com.domus.api.modules.usuario.busca.BuscaUsuarioService;
import com.domus.api.modules.visitante.busca.BuscaVisitanteService;
import com.domus.api.shared.DTO.ResultadoBusca;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuscaGlobalServiceTest {

    BuscaPessoaService buscaPessoaService;
    BuscaEventoService buscaEventoService;
    BuscaUsuarioService buscaUsuarioService;
    BuscaMovimentacaoService buscaMovimentacaoService;
    BuscaCategoriaService buscaCategoriaService;
    BuscaCelulaService buscaCelulaService;
    BuscaVisitanteService buscaVisitanteService;
    BuscaMinisterioService buscaMinisterioService;
    FamiliaIgrejaService familiaIgrejaService;
    BuscaGlobalService service;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        buscaPessoaService = mock(BuscaPessoaService.class);
        buscaEventoService = mock(BuscaEventoService.class);
        buscaUsuarioService = mock(BuscaUsuarioService.class);
        buscaMovimentacaoService = mock(BuscaMovimentacaoService.class);
        buscaCategoriaService = mock(BuscaCategoriaService.class);
        buscaCelulaService = mock(BuscaCelulaService.class);
        buscaVisitanteService = mock(BuscaVisitanteService.class);
        buscaMinisterioService = mock(BuscaMinisterioService.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        service = new BuscaGlobalService(buscaPessoaService, buscaEventoService, buscaUsuarioService,
                buscaMovimentacaoService, buscaCategoriaService, buscaCelulaService, buscaVisitanteService,
                buscaMinisterioService, familiaIgrejaService);

        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId)).thenReturn(Set.of(igrejaId));
    }

    private ResultadoBusca visitante() {
        return new ResultadoBusca(UUID.randomUUID().toString(), TipoEntidadeOutbox.VISITANTE, "Fulano", "Visitante");
    }

    @Test
    void acessoComumNaoVeVisitanteNaBuscaGlobal() {
        when(buscaVisitanteService.buscar(any(), any(), anyInt())).thenReturn(List.of(visitante()));

        List<ResultadoBusca> resultados = service.buscar("fulano", igrejaId, "ACESSO_COMUM", Set.of());

        assertThat(resultados).noneMatch(r -> r.tipo() == TipoEntidadeOutbox.VISITANTE);
        verify(buscaVisitanteService, never()).buscar(any(), any(), anyInt());
    }

    @Test
    void adminVeVisitanteNaBuscaGlobal() {
        when(buscaVisitanteService.buscar("fulano", igrejaId, 5)).thenReturn(List.of(visitante()));

        List<ResultadoBusca> resultados = service.buscar("fulano", igrejaId, "ADMIN_IGREJA", Set.of());

        assertThat(resultados).anyMatch(r -> r.tipo() == TipoEntidadeOutbox.VISITANTE);
    }

    @Test
    void secretarioVeVisitanteNaBuscaGlobalMesmoSemSerAdmin() {
        when(buscaVisitanteService.buscar("fulano", igrejaId, 5)).thenReturn(List.of(visitante()));

        List<ResultadoBusca> resultados = service.buscar("fulano", igrejaId, "ACESSO_COMUM", Set.of("SECRETARIO"));

        assertThat(resultados).anyMatch(r -> r.tipo() == TipoEntidadeOutbox.VISITANTE);
    }
}
