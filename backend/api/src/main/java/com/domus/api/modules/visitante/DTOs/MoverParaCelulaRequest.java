package com.domus.api.modules.visitante.DTOs;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record MoverParaCelulaRequest(
        @NotNull(message = "O celulaId é obrigatório.") UUID celulaId
) {}
