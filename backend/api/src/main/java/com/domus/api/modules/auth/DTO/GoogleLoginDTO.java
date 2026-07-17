package com.domus.api.modules.auth.DTO;

import jakarta.validation.constraints.NotBlank;

public record GoogleLoginDTO(@NotBlank(message = "idToken é obrigatório") String idToken) {}
