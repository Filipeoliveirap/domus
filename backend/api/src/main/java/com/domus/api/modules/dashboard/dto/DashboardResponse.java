package com.domus.api.modules.dashboard.dto;

import com.domus.api.modules.financeiro.movimentacao.DTOs.MovimentacaoResponse;
import com.domus.api.modules.inicio.dto.EventoResumoDTO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.util.List;

public record DashboardResponse(
        Pessoas pessoas,
        Eventos eventos,
        Financeiro financeiro,
        List<MovimentacaoResponse> movimentacoesRecentes,
        List<EventoResumoDTO> proximosEventos
) {
    public record Pessoas(long total, long novosMes) {}

    public record Eventos(long mes, long semana) {}

    public record Financeiro(
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal entradasMes,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal saidasMes,
            @JsonSerialize(using = ToStringSerializer.class) BigDecimal saldoMes
    ) {}
}
