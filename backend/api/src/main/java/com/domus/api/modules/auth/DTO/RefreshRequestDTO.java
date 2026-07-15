package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequestDTO(
        @NotBlank(message = "Refresh token é obrigatório")
        String refreshToken
) {
}
