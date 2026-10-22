package com.domus.api.modules.pagamento.assinatura.dto;

import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.validation.constraints.NotNull;

public record ContratarPlanoRequest(
        @NotNull(message = "O plano é obrigatório.")
        PlanoAssinatura plano,
        String cardTokenId
) {}
