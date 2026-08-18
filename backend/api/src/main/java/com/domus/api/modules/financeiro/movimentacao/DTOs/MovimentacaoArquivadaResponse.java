package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** DTO enxuto pra tela de Arquivadas — sem categoria/contribuintes resolvidos como {@link MovimentacaoResponse}. */
public record MovimentacaoArquivadaResponse(
        UUID id,
        String descricao,
        TipoMovimentacao tipo,
        BigDecimal valor,
        LocalDate dataMovimentacao,
        boolean temContribuinte
) {
    public static MovimentacaoArquivadaResponse de(MovimentacaoFinanceira m, boolean temContribuinte) {
        return new MovimentacaoArquivadaResponse(
                m.getId(), m.getDescricao(), m.getTipo(), m.getValor(), m.getDataMovimentacao(), temContribuinte
        );
    }
}
