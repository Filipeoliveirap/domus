package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.util.UUID;

public record ContribuinteResponse(
        UUID pessoaId,
        String pessoaNome,
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal valor
) {}
