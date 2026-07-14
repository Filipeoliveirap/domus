package com.domus.api.modules.financeiro.relatorio;

<<<<<<< HEAD
import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceiraRepository;
=======
>>>>>>> develop
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.financeiro.relatorio.DTOs.*;
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
    public ResumoPeriodoResponse resumoPorPeriodo(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim) {
        log.info("Gerando resumo financeiro. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        var atual = repository.agregarResumo(igrejaId, dataInicio, dataFim);
        BigDecimal entradas = atual.getTotalEntradas();
        BigDecimal saidas = atual.getTotalSaidas();
        BigDecimal saldo = entradas.subtract(saidas);

        long dias = ChronoUnit.DAYS.between(dataInicio, dataFim) + 1;
        LocalDate fimAnterior = dataInicio.minusDays(1);
        LocalDate inicioAnterior = fimAnterior.minusDays(dias - 1);
        var anterior = repository.agregarResumo(igrejaId, inicioAnterior, fimAnterior);

        var comparacao = new ResumoPeriodoResponse.VariacaoPercentual(
                calcularVariacao(anterior.getTotalEntradas(), entradas),
                calcularVariacao(anterior.getTotalSaidas(), saidas),
                calcularVariacao(anterior.getTotalEntradas().subtract(anterior.getTotalSaidas()), saldo)
        );

        return new ResumoPeriodoResponse(entradas, saidas, saldo, atual.getQuantidade(), comparacao);
    }

    @Transactional(readOnly = true)
    public List<CategoriaBreakdownResponse> porCategoria(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim) {
        log.info("Gerando relatório por categoria. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        var agregados = repository.agregarPorCategoria(igrejaId, dataInicio, dataFim);

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
    public List<EvolucaoMensalResponse> evolucaoMensal(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim) {
        log.info("Gerando evolução mensal. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);

        return repository.agregarEvolucaoMensal(igrejaId, dataInicio, dataFim).stream()
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
<<<<<<< HEAD
=======

    @Transactional(readOnly = true)
    public MaiorLancamentoResponse maiorLancamento(UUID igrejaId, LocalDate dataInicio, LocalDate dataFim) {
        log.info("Buscando maior lançamento. periodo={} a {}, igreja_id={}", dataInicio, dataFim, igrejaId);
        var m = repository.buscarMaiorLancamento(igrejaId, dataInicio, dataFim);
        if (m == null) {
            return null;
        }
        return new MaiorLancamentoResponse(
                m.getId(), m.getDescricao(), m.getCategoriaNome(),
                m.getTipo(), m.getValor(), m.getDataMovimentacao());
    }
>>>>>>> develop
}