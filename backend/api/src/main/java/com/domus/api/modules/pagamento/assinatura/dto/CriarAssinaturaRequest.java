package com.domus.api.modules.pagamento.assinatura.dto;

import jakarta.validation.constraints.NotBlank;

public record CriarAssinaturaRequest(
    @NotBlank String cardTokenId,
    @NotBlank String payerEmail,
    @NotBlank String cpfTitular
) {}
