package com.domus.api.modules.financeiro.relatorio.DTOs;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;

import java.math.BigDecimal;
import java.util.UUID;

public record ContribuinteBreakdownResponse(
        UUID pessoaId,
        String pessoaNome,
        TipoMovimentacao tipo,
        BigDecimal total,
        BigDecimal percentual
) {}
