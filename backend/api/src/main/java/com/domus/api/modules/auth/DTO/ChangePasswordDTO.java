package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordDTO(
        @NotBlank(message = "Senha atual é obrigatória")
        @Size(max = 255, message = "Senha atual inválida")
        String senhaAtual,

        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 255, message = "A nova senha deve ter entre 8 e 255 caracteres")
        String novaSenha
) {}
