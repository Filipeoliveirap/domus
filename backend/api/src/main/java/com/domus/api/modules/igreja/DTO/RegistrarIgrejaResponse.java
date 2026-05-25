package com.domus.api.modules.igreja.DTO;

import java.util.UUID;

public record RegistrarIgrejaResponse (
        String token,
        String nome,
        String role,
        UUID igrejaId
)
{ }
