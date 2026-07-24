package com.domus.api.modules.ministerio.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MinisterioRequest(
        @NotBlank(message = "O nome do ministério é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome
) {}
