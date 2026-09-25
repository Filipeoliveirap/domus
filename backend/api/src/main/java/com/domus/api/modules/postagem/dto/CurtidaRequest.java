package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.postagem.TipoReacao;
import jakarta.validation.constraints.NotNull;

public record CurtidaRequest(
        @NotNull(message = "Tipo de reação é obrigatório")
        TipoReacao tipo
) {}
