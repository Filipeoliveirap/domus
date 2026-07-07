package com.domus.api.modules.financeiro.relatorio.DTOs;

import java.math.BigDecimal;

public record EvolucaoMensalResponse(
        int ano,
        int mes,
        BigDecimal entradas,
        BigDecimal saidas,
        BigDecimal saldo
) {}