package com.domus.api.modules.pagamento.DTOs;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

/** Corpo de POST /eventos/simular-pagamento — o form de evento pede as opções antes de salvar. */
public record SimularPagamentoRequest(
    @NotNull(message = "O valor é obrigatório.")
    @Positive(message = "O valor deve ser maior que zero.")
    BigDecimal preco,
    Boolean aceitaCartao,
    Integer maxParcelas
) {}
