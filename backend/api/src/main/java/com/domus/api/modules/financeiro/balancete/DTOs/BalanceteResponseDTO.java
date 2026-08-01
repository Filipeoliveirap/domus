package com.domus.api.modules.financeiro.balancete.DTOs;

import java.math.BigDecimal;
import java.util.List;

public record BalanceteResponseDTO(
        int ano,
        BigDecimal saldoAbertura,
        List<LinhaCategoriaDTO> entradas,
        List<LinhaCategoriaDTO> saidas,
        List<BigDecimal> subtotalEntradasPorMes,
        List<BigDecimal> subtotalSaidasPorMes,
        List<BigDecimal> saldoDoMes,
        List<BigDecimal> saldoAcumulado
) {}
