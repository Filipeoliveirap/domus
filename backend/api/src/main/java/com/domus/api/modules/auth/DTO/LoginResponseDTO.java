package com.domus.api.modules.auth.DTO;

import java.util.UUID;

public record LoginResponseDTO(
        String nome,
        String role,
        UUID igrejaId,
        String token) {
}
