package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.pessoa.Vinculo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelatorioServiceTest {

    RelatorioRepository repository;
    RelatorioService service;

    UUID igrejaId = UUID.randomUUID();
    LocalDate dataInicio = LocalDate.of(2026, 7, 1);
    LocalDate dataFim = LocalDate.of(2026, 7, 31);

    @BeforeEach
    void setup() {
        repository = mock(RelatorioRepository.class);
        service = new RelatorioService(repository);
    }

    private RelatorioProjections.ResumoAgregado resumo(BigDecimal entradas, BigDecimal saidas, long quantidade) {
        RelatorioProjections.ResumoAgregado r = mock(RelatorioProjections.ResumoAgregado.class);
        when(r.getTotalEntradas()).thenReturn(entradas);
        when(r.getTotalSaidas()).thenReturn(saidas);
        when(r.getQuantidade()).thenReturn(quantidade);
        return r;
    }

    @Test
    void semVinculoUsaTotalDaMovimentacaoInteira() {
        var resumo = resumo(new BigDecimal("100.00"), BigDecimal.ZERO, 1);
        when(repository.agregarResumo(eq(igrejaId), any(), any())).thenReturn(resumo);

        var response = service.resumoPorPeriodo(igrejaId, dataInicio, dataFim, null);

        assertThat(response.totalEntradas()).isEqualByComparingTo("100.00");
        verify(repository, never()).agregarResumoPorVinculo(any(), any(), any(), any());
    }

    @Test
    void comVinculoFatiaPeloValorDoContribuinteNaoPelaMovimentacaoInteira() {
        // Movimentação de R$100,00 dividida 60/40 entre um contribuinte MEMBRO e um CONGREGANTE.
        // Filtrando por MEMBRO, o relatório deve somar só os R$60,00 daquele contribuinte.
        var resumo = resumo(new BigDecimal("60.00"), BigDecimal.ZERO, 1);
        when(repository.agregarResumoPorVinculo(eq(igrejaId), any(), any(), eq(Vinculo.MEMBRO))).thenReturn(resumo);

        var response = service.resumoPorPeriodo(igrejaId, dataInicio, dataFim, Vinculo.MEMBRO);

        assertThat(response.totalEntradas()).isEqualByComparingTo("60.00");
        verify(repository, never()).agregarResumo(any(), any(), any());
    }

    @Test
    void porContribuinteAgregaTotalPorPessoaComPercentual() {
        RelatorioProjections.ContribuinteAgregado a = mock(RelatorioProjections.ContribuinteAgregado.class);
        UUID pessoaId = UUID.randomUUID();
        when(a.getPessoaId()).thenReturn(pessoaId);
        when(a.getPessoaNome()).thenReturn("Fulano");
        when(a.getTipo()).thenReturn(TipoMovimentacao.ENTRADA);
        when(a.getTotal()).thenReturn(new BigDecimal("60.00"));

        when(repository.agregarPorContribuinte(eq(igrejaId), any(), any())).thenReturn(java.util.List.of(a));

        var response = service.porContribuinte(igrejaId, dataInicio, dataFim, null);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).pessoaNome()).isEqualTo("Fulano");
        assertThat(response.get(0).percentual()).isEqualByComparingTo("100.0");
    }
}
