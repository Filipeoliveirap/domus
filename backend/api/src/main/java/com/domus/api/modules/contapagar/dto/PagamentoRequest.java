package com.domus.api.modules.contapagar.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.domus.api.modules.contapagar.FormaPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request para registrar um pagamento de conta. */
public record PagamentoRequest(
    @JsonAlias({"valor", "valor_pago"})
    BigDecimal valorPago,

    @JsonAlias({"valor_liquido"})
    BigDecimal valorLiquido,

    @JsonAlias({"juros_acrescimos"})
    BigDecimal jurosAcrescimos,

    BigDecimal desconto,

    @NotNull(message = "Data do pagamento é obrigatória.")
    @JsonFormat(pattern = "yyyy-MM-dd")
    LocalDate data,

    @JsonAlias({"meioPagamento", "meio_pagamento"})
    FormaPagamento forma,

    UUID anexoId
) {
    public PagamentoRequest {
        if (jurosAcrescimos == null) jurosAcrescimos = BigDecimal.ZERO;
        if (desconto == null) desconto = BigDecimal.ZERO;
        if (valorPago == null) valorPago = BigDecimal.ZERO;
        if (valorLiquido == null) {
            valorLiquido = valorPago.add(jurosAcrescimos).subtract(desconto);
            if (valorLiquido.compareTo(BigDecimal.ZERO) < 0) {
                valorLiquido = BigDecimal.ZERO;
            }
        }
    }
}
