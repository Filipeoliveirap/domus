package com.domus.api.modules.financeiro.balancete;

import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteFamiliaResponseDTO;
import com.domus.api.modules.financeiro.balancete.DTOs.BalanceteResponseDTO;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.CategoriaFinanceiraRepository;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.igreja.familia.FamiliaIgrejaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BalanceteServiceTest {

    BalanceteRepository repository;
    CategoriaFinanceiraRepository categoriaRepository;
    FamiliaIgrejaService familiaIgrejaService;
    IgrejaRepository igrejaRepository;
    BalanceteService service;

    UUID igrejaId = UUID.randomUUID();

    @BeforeEach
    void setup() {
        repository = mock(BalanceteRepository.class);
        categoriaRepository = mock(CategoriaFinanceiraRepository.class);
        familiaIgrejaService = mock(FamiliaIgrejaService.class);
        igrejaRepository = mock(IgrejaRepository.class);
        service = new BalanceteService(repository, categoriaRepository, familiaIgrejaService, igrejaRepository);
        when(repository.saldoAntesDe(eq(igrejaId), any())).thenReturn(BigDecimal.ZERO);
    }

    private CategoriaFinanceira categoriaAtiva(UUID id, String nome, TipoCategoria tipo) {
        return CategoriaFinanceira.builder().id(id).nome(nome).tipo(tipo).build();
    }

    private BalanceteProjections.LinhaMensalAgregada linha(UUID categoriaId, String nome,
            boolean arquivada, String tipo, int mes, BigDecimal total) {
        BalanceteProjections.LinhaMensalAgregada l = mock(BalanceteProjections.LinhaMensalAgregada.class);
        when(l.getCategoriaId()).thenReturn(categoriaId);
        when(l.getNomeCategoria()).thenReturn(nome);
        when(l.getArquivada()).thenReturn(arquivada);
        when(l.getTipo()).thenReturn(tipo);
        when(l.getMes()).thenReturn(mes);
        when(l.getTotal()).thenReturn(total);
        return l;
    }

    @Test
    void categoriaAtivaSemMovimentoNoAnoApareceZerada() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Dizimos", TipoCategoria.ENTRADA)));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of());

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.entradas().get(0).arquivada()).isFalse();
        assertThat(resultado.entradas().get(0).totalAno()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resultado.entradas().get(0).valoresPorMes()).hasSize(12);
        assertThat(resultado.entradas().get(0).valoresPorMes()).allMatch(v -> v.compareTo(BigDecimal.ZERO) == 0);
    }

    @Test
    void categoriaArquivadaSemMovimentoNoAnoNaoAparece() {
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of());

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).isEmpty();
        assertThat(resultado.saidas()).isEmpty();
    }

    @Test
    void categoriaArquivadaComMovimentoApareceMarcada() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId)).thenReturn(List.of());
        var l1 = linha(categoriaId, "Doacao Especial", true, "ENTRADA", 3, new BigDecimal("200.00"));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(l1));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.entradas().get(0).arquivada()).isTrue();
        assertThat(resultado.entradas().get(0).valoresPorMes().get(2)).isEqualByComparingTo("200.00"); // março = índice 2
        assertThat(resultado.entradas().get(0).totalAno()).isEqualByComparingTo("200.00");
    }

    @Test
    void categoriaAmbosApareceEmEntradasESaidasSeparadamente() {
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Ofertas", TipoCategoria.AMBOS)));
        var l1 = linha(categoriaId, "Ofertas", false, "ENTRADA", 1, new BigDecimal("100.00"));
        var l2 = linha(categoriaId, "Ofertas", false, "SAIDA", 1, new BigDecimal("40.00"));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(l1, l2));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.saidas()).hasSize(1);
        assertThat(resultado.entradas().get(0).valoresPorMes().get(0)).isEqualByComparingTo("100.00");
        assertThat(resultado.saidas().get(0).valoresPorMes().get(0)).isEqualByComparingTo("40.00");
    }

    @Test
    void saldoAcumuladoSomaAberturaMaisSaldoCorridoDoAno() {
        UUID categoriaId = UUID.randomUUID();
        when(repository.saldoAntesDe(eq(igrejaId), eq(LocalDate.of(2026, 1, 1))))
                .thenReturn(new BigDecimal("1000.00"));
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Dizimos", TipoCategoria.ENTRADA)));
        var l1 = linha(categoriaId, "Dizimos", false, "ENTRADA", 1, new BigDecimal("300.00"));
        var l2 = linha(categoriaId, "Dizimos", false, "ENTRADA", 2, new BigDecimal("200.00"));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(l1, l2));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        // jan: 1000 + 300 = 1300 | fev: 1300 + 200 = 1500
        assertThat(resultado.saldoAcumulado().get(0)).isEqualByComparingTo("1300.00");
        assertThat(resultado.saldoAcumulado().get(1)).isEqualByComparingTo("1500.00");
        assertThat(resultado.saldoAcumulado().get(11)).isEqualByComparingTo("1500.00"); // sem movimento depois, mantém
    }

    @Test
    void categoriaAtivaComTipoAlteradoAindaMostraMovimentacoesAntigasDaOutraDirecao() {
        // Cenário: categoria "Ofertas" era AMBOS quando recebeu entradas E saídas.
        // O admin depois trocou o tipo dela pra ENTRADA. A categoria continua ativa,
        // mas a query de agregação ainda traz a linha SAIDA antiga - ela não pode sumir.
        UUID categoriaId = UUID.randomUUID();
        when(categoriaRepository.buscarTodasPorIgreja(igrejaId))
                .thenReturn(List.of(categoriaAtiva(categoriaId, "Ofertas", TipoCategoria.ENTRADA)));
        var l1 = linha(categoriaId, "Ofertas", false, "ENTRADA", 1, new BigDecimal("100.00"));
        var l2 = linha(categoriaId, "Ofertas", false, "SAIDA", 3, new BigDecimal("40.00"));
        when(repository.agregarPorCategoriaEMes(igrejaId, 2026)).thenReturn(List.of(l1, l2));

        BalanceteResponseDTO resultado = service.gerar(igrejaId, 2026);

        assertThat(resultado.entradas()).hasSize(1);
        assertThat(resultado.entradas().get(0).valoresPorMes().get(0)).isEqualByComparingTo("100.00");
        assertThat(resultado.saidas()).hasSize(1);
        assertThat(resultado.saidas().get(0).arquivada()).isFalse();
        assertThat(resultado.saidas().get(0).valoresPorMes().get(2)).isEqualByComparingTo("40.00");
        assertThat(resultado.saidas().get(0).totalAno()).isEqualByComparingTo("40.00");
    }

    @Test
    void categoriaConsolidadaSoMarcaArquivadaSeNaoAtivaEmNenhumaIgrejaDaFamilia() {
        UUID sedeId = UUID.randomUUID();
        UUID congregacaoId = UUID.randomUUID();
        UUID igrejaMaeParaChamada = sedeId;

        when(familiaIgrejaService.idsDaFamilia(sedeId)).thenReturn(List.of(sedeId, congregacaoId));
        when(igrejaRepository.findAllById(List.of(sedeId, congregacaoId))).thenReturn(List.of(
                Igreja.builder().id(sedeId).nome("Sede").build(),
                Igreja.builder().id(congregacaoId).nome("Congregacao").igrejaMae(Igreja.builder().id(sedeId).build()).build()
        ));
        when(categoriaRepository.buscarTodasPorIgreja(sedeId)).thenReturn(List.of());
        when(categoriaRepository.buscarTodasPorIgreja(congregacaoId)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(sedeId, 2026)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(congregacaoId, 2026)).thenReturn(List.of());
        when(repository.saldoAntesDe(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.saldoAntesDeVariasIgrejas(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.agregarConsolidadoPorCategoriaEMes(List.of(sedeId, congregacaoId), 2026))
                .thenReturn(List.of());

        BalanceteFamiliaResponseDTO resultado = service.gerarFamilia(igrejaMaeParaChamada, 2026);

        assertThat(resultado.porIgreja()).hasSize(2);
        assertThat(resultado.porIgreja().get(0).ehSede()).isTrue();
        assertThat(resultado.porIgreja().get(1).ehSede()).isFalse();
        assertThat(resultado.consolidado().entradas()).isEmpty();
    }

    @Test
    void categoriaConsolidadaAtivaEmApenasUmaIgrejaDaFamiliaNaoApareceArquivada() {
        UUID sedeId = UUID.randomUUID();
        UUID congregacaoId = UUID.randomUUID();

        when(familiaIgrejaService.idsDaFamilia(sedeId)).thenReturn(List.of(sedeId, congregacaoId));
        when(igrejaRepository.findAllById(List.of(sedeId, congregacaoId))).thenReturn(List.of(
                Igreja.builder().id(sedeId).nome("Sede").build(),
                Igreja.builder().id(congregacaoId).nome("Congregacao").igrejaMae(Igreja.builder().id(sedeId).build()).build()
        ));
        // "Dizimos" só está ativa na sede, não na congregação
        when(categoriaRepository.buscarTodasPorIgreja(sedeId))
                .thenReturn(List.of(categoriaAtiva(UUID.randomUUID(), "Dizimos", TipoCategoria.ENTRADA)));
        when(categoriaRepository.buscarTodasPorIgreja(congregacaoId)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(sedeId, 2026)).thenReturn(List.of());
        when(repository.agregarPorCategoriaEMes(congregacaoId, 2026)).thenReturn(List.of());
        when(repository.saldoAntesDe(any(), any())).thenReturn(BigDecimal.ZERO);
        when(repository.saldoAntesDeVariasIgrejas(any(), any())).thenReturn(BigDecimal.ZERO);

        BalanceteProjections.LinhaMensalConsolidada linhaConsolidada = mock(BalanceteProjections.LinhaMensalConsolidada.class);
        when(linhaConsolidada.getChave()).thenReturn("dizimos");
        when(linhaConsolidada.getNomeExibicao()).thenReturn("Dizimos");
        when(linhaConsolidada.getTipo()).thenReturn("ENTRADA");
        when(linhaConsolidada.getMes()).thenReturn(1);
        when(linhaConsolidada.getTotal()).thenReturn(new BigDecimal("500.00"));
        when(repository.agregarConsolidadoPorCategoriaEMes(List.of(sedeId, congregacaoId), 2026))
                .thenReturn(List.of(linhaConsolidada));

        BalanceteFamiliaResponseDTO resultado = service.gerarFamilia(sedeId, 2026);

        assertThat(resultado.consolidado().entradas()).hasSize(1);
        assertThat(resultado.consolidado().entradas().get(0).arquivada()).isFalse();
        assertThat(resultado.consolidado().entradas().get(0).nomeCategoria()).isEqualTo("Dizimos");
        assertThat(resultado.consolidado().entradas().get(0).valoresPorMes().get(0)).isEqualByComparingTo("500.00");
    }
}
