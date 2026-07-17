package com.domus.api.modules.financeiro.relatorio.DTOs;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MaiorLancamentoResponse(
        UUID id,
        String descricao,
        String categoriaNome,
        TipoMovimentacao tipo,
        BigDecimal valor,
        LocalDate dataMovimentacao
) {}