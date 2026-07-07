package com.domus.api.modules.usuario.DTO;

import jakarta.validation.constraints.NotNull;

public record UpdateStatusRequest(
        @NotNull(message = "O status é obrigatório.")
        Boolean ativo
) {}