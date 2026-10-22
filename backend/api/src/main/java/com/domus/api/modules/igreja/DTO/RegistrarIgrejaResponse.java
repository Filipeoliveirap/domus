package com.domus.api.modules.igreja.DTO;

import java.util.UUID;

public record RegistrarIgrejaResponse (
        UUID id,
        String token,
        String refreshToken,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome,
        String codigoVinculo
) {
    public RegistrarIgrejaResponse(UUID id, String token, String refreshToken, String nome, String role, UUID igrejaId, String igrejaNome) {
        this(id, token, refreshToken, nome, role, igrejaId, igrejaNome, null);
    }
}
