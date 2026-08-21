package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthenticationDTO(
        @NotBlank(message = "E-mail é obrigatório")
        @Email(message = "E-mail inválido")
        @Size(max = 255, message = "E-mail inválido")
        String email,
        @NotBlank(message = "Senha é obrigatória")
        @Size(max = 255, message = "Senha inválida")
        String senha
) {
}
