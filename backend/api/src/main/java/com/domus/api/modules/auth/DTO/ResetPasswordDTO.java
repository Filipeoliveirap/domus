package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordDTO(
        @NotBlank(message = "Token é obrigatório")
        @Size(max = 4096, message = "Token inválido")
        String token,
        @NotBlank(message = "Nova senha é obrigatória")
        @Size(min = 8, max = 255, message = "A senha deve ter entre 8 e 255 caracteres")
        String novaSenha
) {
}
