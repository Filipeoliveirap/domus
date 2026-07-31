package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.financeiro.relatorio.DTOs.*;
import com.domus.api.modules.pessoa.Vinculo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RelatorioService {

    private final RelatorioRepository repository;

    @Transactional(readOnly = true)
    public ResumoPeriodoResponse resumoPorPeriodo(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim, Vinculo vinculo) {
        log.info("Gerando resumo financeiro. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        var atual = vinculo == null
                ? repository.agregarResumo(igrejaId, dataInicio, dataFim)
                : repository.agregarResumoPorVinculo(igrejaId, dataInicio, dataFim, vinculo);
        BigDecimal entradas = atual.getTotalEntradas();
        BigDecimal saidas = atual.getTotalSaidas();
        BigDecimal saldo = entradas.subtract(saidas);

        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        LocalDate fimAnterior = dataInicio.minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(dias - 1);
        var anterior = vinculo == null
                ? repository.agregarResumo(igrejaId, inicioAnterior, fimAnterior)
                : repository.agregarResumoPorVinculo(igrejaId, inicioAnterior, fimAnterior, vinculo);

        var comparacao = new ResumoPeriodoResponse.VariacaoPercentual(
                calcularVariacao(anterior.getTotalEntradas(), entradas),
                calcularVariacao(anterior.getTotalSaidas(), saidas),
                calcularVariacao(anterior.getTotalEntradas().subtract(anterior.getTotalSaidas()), saldo)
        );

        return new ResumoPeriodoResponse(entradas, saidas, saldo, atual.getQuantidade(), comparacao);
    }

    @Transactional(readOnly = true)
    public List<CategoriaBreakdownResponse> porCategoria(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim, Vinculo vinculo) {
        log.info("Gerando relatório por categoria. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        var agregados = vinculo == null
                ? repository.agregarPorCategoria(igrejaId, dataInicio, dataFim)
                : repository.agregarPorCategoriaPorVinculo(igrejaId, dataInicio, dataFim, vinculo);

        BigDecimal totalEntradas = agregados.stream()
                .filter(a -> a.getTipo() == TipoMovimentacao.ENTRADA)
                .map(a -> a.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSaidas = agregados.stream()
                .filter(a -> a.getTipo() == TipoMovimentacao.SAIDA)
                .map(a -> a.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);

        return agregados.stream().map(a -> {
            BigDecimal base = a.getTipo() == TipoMovimentacao.ENTRADA ? totalEntradas : totalSaidas;
            BigDecimal pct = base.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : a.getTotal().multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP);
            return new CategoriaBreakdownResponse(
                    a.getCategoriaId(), a.getCategoriaNome(), a.getTipo(), a.getTotal(), pct);
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<EvolucaoMensalResponse> evolucaoMensal(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim, Vinculo vinculo) {
        log.info("Gerando evolução mensal. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        var agregados = vinculo == null
                ? repository.agregarEvolucaoMensal(igrejaId, dataInicio, dataFim)
                : repository.agregarEvolucaoMensalPorVinculo(igrejaId, dataInicio, dataFim, vinculo);

        return agregados.stream()
                .map(m -> new EvolucaoMensalResponse(
                        m.getAno(), m.getMes(), m.getEntradas(), m.getSaidas(),
                        m.getEntradas().subtract(m.getSaidas())))
                .toList();
    }

    private BigDecimal calcularVariacao(BigDecimal anterior, BigDecimal atual) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) {
            return atual.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(100);
        }
        return atual.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior, 1, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public MaiorLancamentoResponse maiorLancamento(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim, Vinculo vinculo) {
        log.info("Buscando maior lançamento. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);
        var m = vinculo == null
                ? repository.buscarMaiorLancamento(igrejaId, dataInicio, dataFim)
                : repository.buscarMaiorLancamentoPorVinculo(igrejaId, dataInicio, dataFim, vinculo);
        if (m == null) {
            return null;
        }
        return new MaiorLancamentoResponse(
                m.getId(), m.getDescricao(), m.getCategoriaNome(),
                m.getTipo(), m.getValor(), m.getDataMovimentacao());
    }

    @Transactional(readOnly = true)
    public List<ContribuinteBreakdownResponse> porContribuinte(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim, Vinculo vinculo) {
        log.info("Gerando relatório por contribuinte. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        var agregados = vinculo == null
                ? repository.agregarPorContribuinte(igrejaId, dataInicio, dataFim)
                : repository.agregarPorContribuintePorVinculo(igrejaId, dataInicio, dataFim, vinculo);

        BigDecimal totalEntradas = agregados.stream()
                .filter(a -> a.getTipo() == TipoMovimentacao.ENTRADA)
                .map(a -> a.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSaidas = agregados.stream()
                .filter(a -> a.getTipo() == TipoMovimentacao.SAIDA)
                .map(a -> a.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);

        return agregados.stream().map(a -> {
            BigDecimal base = a.getTipo() == TipoMovimentacao.ENTRADA ? totalEntradas : totalSaidas;
            BigDecimal pct = base.compareTo(BigDecimal.ZERO) == 0
                    ? BigDecimal.ZERO
                    : a.getTotal().multiply(BigDecimal.valueOf(100)).divide(base, 1, RoundingMode.HALF_UP);
            return new ContribuinteBreakdownResponse(
                    a.getPessoaId(), a.getPessoaNome(), a.getTipo(), a.getTotal(), pct);
        }).toList();
    }
}