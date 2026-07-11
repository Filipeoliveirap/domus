package com.domus.api.modules.auth.DTO;

import java.util.UUID;

public record LoginResponseDTO(
        UUID id,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome,
        String token

) {

}
