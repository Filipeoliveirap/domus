package com.domus.api.modules.financeiro.balancete.DTOs;

import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record BalanceteResponseDTO(
        int ano,
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal saldoAbertura,
        List<LinhaCategoriaDTO> entradas,
        List<LinhaCategoriaDTO> saidas,
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        List<BigDecimal> subtotalEntradasPorMes,
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        List<BigDecimal> subtotalSaidasPorMes,
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        List<BigDecimal> saldoDoMes,
        @JsonSerialize(contentUsing = ToStringSerializer.class)
        List<BigDecimal> saldoAcumulado
) {}
