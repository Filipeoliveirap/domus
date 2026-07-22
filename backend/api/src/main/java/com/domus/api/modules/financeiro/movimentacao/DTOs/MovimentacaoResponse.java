package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record MovimentacaoResponse(
        UUID id,
        TipoMovimentacao tipo,
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal valor,
        LocalDate dataMovimentacao,
        String descricao,
        UUID categoriaId,
        String categoriaNome,
        UUID pessoaId,
        String pessoaNome,
        String criadoPorNome,
        String atualizadoPorNome
) {
    public static MovimentacaoResponse de(MovimentacaoFinanceira m) {
        return new MovimentacaoResponse(
                m.getId(),
                m.getTipo(),
                m.getValor(),
                m.getDataMovimentacao(),
                m.getDescricao(),
                m.getCategoria().getId(),
                m.getCategoria().getNome(),
                m.getPessoa() != null ? m.getPessoa().getId() : null,
                m.getPessoa() != null ? m.getPessoa().getNome() : null,
                m.getCriadoPor().getNome(),
                m.getAtualizadoPor() != null ? m.getAtualizadoPor().getNome() : null
        );
    }
}