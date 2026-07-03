package com.domus.api.modules.evento.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record EventoRequest(
        @NotBlank(message = "O título é obrigatório.")
        String titulo,
        String descricao,
        @NotNull(message = "A data de início é obrigatória.")
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        String local,
        String foto
) {}
