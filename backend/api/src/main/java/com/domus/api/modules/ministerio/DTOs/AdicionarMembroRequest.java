package com.domus.api.modules.ministerio.DTOs;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AdicionarMembroRequest(@NotNull(message = "A pessoa é obrigatória.") UUID pessoaId) {}
