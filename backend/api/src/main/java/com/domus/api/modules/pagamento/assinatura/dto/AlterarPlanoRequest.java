package com.domus.api.modules.pagamento.assinatura.dto;

import com.domus.api.modules.igreja.PlanoAssinatura;
import jakarta.validation.constraints.NotNull;

public record AlterarPlanoRequest(
    @NotNull(message = "Novo plano é obrigatório")
    PlanoAssinatura novoPlano
) {}
