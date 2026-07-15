package com.domus.api.modules.igreja.DTO;

import java.util.UUID;

public record RegistrarIgrejaResponse (
        UUID id,
        String token,
        String refreshToken,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome
)
{ }
