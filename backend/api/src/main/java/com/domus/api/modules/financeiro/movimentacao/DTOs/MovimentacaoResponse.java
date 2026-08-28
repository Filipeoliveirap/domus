package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.domus.api.modules.financeiro.movimentacao.MovimentacaoFinanceira;
import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import com.domus.api.modules.pessoa.Pessoa;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
        List<ContribuinteResponse> contribuintes,
        String criadoPorNome,
        String atualizadoPorNome,
        boolean arquivada
) {
    /**
     * @param categoriaNome        resolvido à parte, em lote — nunca m.getCategoria().getNome()
     *                             direto, senão categoria arquivada estoura EntityNotFoundException.
     * @param pessoasContribuintes idem, resolvidas via bypass do @SQLRestriction — pessoa
     *                             arquivada (mas não excluída) mostra os dados reais; ausente
     *                             do mapa só quando excluída de vez (pessoa_id já é NULL).
     */
    public static MovimentacaoResponse de(MovimentacaoFinanceira m, String categoriaNome,
                                           Map<UUID, Pessoa> pessoasContribuintes) {
        return new MovimentacaoResponse(
                m.getId(),
                m.getTipo(),
                m.getValor(),
                m.getDataMovimentacao(),
                m.getDescricao(),
                m.getCategoria().getId(),
                categoriaNome,
                m.getContribuintes().stream()
                        .map(c -> {
                            if (c.getNomeExterno() != null) {
                                return new ContribuinteResponse(null, c.getNomeExterno(), c.getNomeExterno(), c.getValor());
                            }
                            Pessoa pessoa = c.getPessoa() == null ? null : pessoasContribuintes.get(c.getPessoa().getId());
                            return new ContribuinteResponse(
                                    pessoa == null ? null : pessoa.getId(),
                                    pessoa == null ? "Pessoa removida do sistema" : pessoa.getNome(),
                                    null,
                                    c.getValor());
                        })
                        .toList(),
                m.getCriadoPor() != null ? m.getCriadoPor().getNome() : m.getCriadoPorTexto(),
                m.getAtualizadoPor() != null ? m.getAtualizadoPor().getNome() : m.getAtualizadoPorTexto(),
                m.getDeletedAt() != null
        );
    }
}