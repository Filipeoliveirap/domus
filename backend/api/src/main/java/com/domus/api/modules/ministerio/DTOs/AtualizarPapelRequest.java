package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Papel;
import jakarta.validation.constraints.NotNull;

public record AtualizarPapelRequest(@NotNull(message = "O papel é obrigatório.") Papel papel) {}
