package com.domus.api.modules.pagamento.assinatura.dto;

import jakarta.validation.constraints.NotBlank;

public record AtualizarCartaoRequest(
    @NotBlank(message = "Token do cartão é obrigatório")
    String cardTokenId
) {}
