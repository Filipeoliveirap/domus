package com.domus.api.modules.financeiro.balancete.DTOs;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record LinhaCategoriaDTO(
        UUID categoriaId,
        String nomeCategoria,
        boolean arquivada,
        List<BigDecimal> valoresPorMes,
        BigDecimal totalAno
) {}
