package com.domus.api.modules.dashboard;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
import com.domus.api.modules.financeiro.relatorio.RelatorioProjections;
import com.domus.api.modules.financeiro.relatorio.RelatorioRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import com.domus.api.modules.pessoa.PessoaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {

    PessoaRepository pessoaRepository;
    EventoRepository eventoRepository;
    RelatorioRepository relatorioRepository;
    MovimentacaoFinanceiraRepository movimentacaoRepository;
    CategoriaFinanceiraRepository categoriaRepository;
    FamiliaIgrejaService familiaIgrejaService;
    DashboardService service;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        pessoaRepository = mock(PessoaRepository.class);
        eventoRepository = mock(EventoRepository.class);
        relatorioRepository = mock(RelatorioRepository.class);
        movimentacaoRepository = mock(MovimentacaoFinanceiraRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        service = new DashboardService(pessoaRepository, eventoRepository, relatorioRepository,
                movimentacaoRepository, categoriaRepository, familiaIgrejaService);

        when(relatorioRepository.agregarResumo(eq(igrejaId), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(new RelatorioProjections.ResumoAgregado() {
                    public BigDecimal getTotalEntradas() { return BigDecimal.ZERO; }
                    public BigDecimal getTotalSaidas() { return BigDecimal.ZERO; }
                    public Long getQuantidade() { return 0L; }
                });
        when(movimentacaoRepository.recentes(eq(igrejaId), any())).thenReturn(List.of());
    }

    private Evento evento(UUID id, UUID igrejaId) {
        Igreja igreja = new Igreja();
        igreja.setId(igrejaId);
        return Evento.builder().id(id).igreja(igreja)
                .titulo("Culto").inicioEm(LocalDateTime.now().plusDays(1)).build();
    }

    @Test
    void proximosEventosIncluiCompartilhadosDaFamilia() {
        UUID outraIgrejaId = UUID.randomUUID();
        when(familiaIgrejaService.idsDaFamiliaCompleta(igrejaId))
                .thenReturn(Set.of(igrejaId, outraIgrejaId));
        when(eventoRepository.proximosDaFamilia(eq(igrejaId), eq(Set.of(igrejaId, outraIgrejaId)),
                any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(evento(UUID.randomUUID(), igrejaId), evento(UUID.randomUUID(), outraIgrejaId)));

        var response = service.carregar(igrejaId);

        assertThat(response.proximosEventos()).hasSize(2);
    }
}
