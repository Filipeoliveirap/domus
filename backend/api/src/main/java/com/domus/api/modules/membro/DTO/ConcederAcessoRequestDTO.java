package com.domus.api.modules.membro.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ConcederAcessoRequestDTO(
        @NotNull(message = "O membro é obrigatório")
        UUID membroId,
        @NotBlank(message = "O perfil é obrigatório")
        String role,
        @NotBlank(message = "A senha é obrigatória")
        @Size(min = 8, message = "A senha deve ter no mínimo 8 caracteres")
        String senha
) {}
