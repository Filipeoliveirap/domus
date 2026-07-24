package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotNull;

public record MarcarPresencaRequest(@NotNull boolean compareceu) {}
