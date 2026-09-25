package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.contapagar.PagamentoConta;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Resposta de um pagamento de conta. */
public record PagamentoResponse(
    UUID id,
    BigDecimal valorPago,
    BigDecimal jurosAcrescimos,
    BigDecimal desconto,
    BigDecimal valorLiquido,
    LocalDate data,
    String forma,
    UUID anexoId,
    UUID movimentacaoId,
    String estornadoPorTexto,
    LocalDateTime estornadoEm
) {
    public static PagamentoResponse from(PagamentoConta p) {
        return new PagamentoResponse(
            p.getId(),
            p.getValorPago(),
            p.getJurosAcrescimos(),
            p.getDesconto(),
            p.getValorLiquido(),
            p.getPagoEm(),
            p.getForma() != null ? p.getForma().name() : null,
            p.getAnexo() != null ? p.getAnexo().getId() : null,
            p.getMovimentacao() != null ? p.getMovimentacao().getId() : null,
            p.getEstornadoPorUsuario() != null ? p.getEstornadoPorUsuario().getPessoa().getNome() : null,
            p.getEstornadoEm()
        );
    }
}
