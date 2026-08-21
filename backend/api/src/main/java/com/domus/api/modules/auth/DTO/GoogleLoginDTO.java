package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginDTO(
        @NotBlank(message = "idToken é obrigatório")
        @Size(max = 4096, message = "idToken inválido")
        String idToken) {}
