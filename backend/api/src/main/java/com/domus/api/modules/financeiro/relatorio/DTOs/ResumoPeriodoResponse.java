package com.domus.api.modules.financeiro.relatorio.DTOs;

import java.math.BigDecimal;

public record ResumoPeriodoResponse(
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        BigDecimal saldo,
        BigDecimal entradasAnterior,
        BigDecimal saidasAnterior,
        BigDecimal saldoAnterior,
        long quantidadeMovimentacoes,
        VariacaoPercentual comparacao
) {
    public record VariacaoPercentual(
            BigDecimal entradasVariacao,
            BigDecimal saidasVariacao,
            BigDecimal saldoVariacao
    ) {}
}