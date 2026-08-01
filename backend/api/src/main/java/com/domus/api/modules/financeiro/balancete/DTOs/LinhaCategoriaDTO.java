package com.domus.api.modules.financeiro.balancete.DTOs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record LinhaCategoriaDTO(
        UUID categoriaId,
        String nomeCategoria,
        boolean arquivada,
        @JsonSerialize(using = ToStringSerializer.class)
        List<BigDecimal> valoresPorMes,
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal totalAno
) {}
