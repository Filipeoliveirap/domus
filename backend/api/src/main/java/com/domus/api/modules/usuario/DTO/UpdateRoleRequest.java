package com.domus.api.modules.usuario.DTO;

import jakarta.validation.constraints.NotBlank;

public record UpdateRoleRequest(
        @NotBlank(message = "O perfil é obrigatório.")
        String role
) {
}
