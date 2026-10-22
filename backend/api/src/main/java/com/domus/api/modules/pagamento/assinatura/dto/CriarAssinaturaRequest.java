package com.domus.api.modules.pagamento.assinatura.dto;

import jakarta.validation.constraints.NotBlank;

public record CriarAssinaturaRequest(
    @NotBlank String cardTokenId,
    @NotBlank String payerEmail,
    String cpfTitular
) {
    public CriarAssinaturaRequest(String cardTokenId, String payerEmail) {
        this(cardTokenId, payerEmail, null);
    }
}
