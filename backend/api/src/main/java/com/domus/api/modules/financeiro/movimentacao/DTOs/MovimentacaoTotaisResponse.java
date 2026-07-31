package com.domus.api.modules.financeiro.movimentacao.DTOs;

import java.math.BigDecimal;

public record MovimentacaoTotaisResponse(
        BigDecimal totalEntradas,
        BigDecimal totalSaidas,
        long quantidade
) {
}
