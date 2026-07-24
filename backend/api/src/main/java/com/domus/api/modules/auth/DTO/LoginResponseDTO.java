package com.domus.api.modules.auth.DTO;

import java.util.UUID;

public record LoginResponseDTO(
        UUID id,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome,
        /** Foto da PESSOA (não do usuário — Usuario não tem foto própria). Null = sem foto. */
        UUID fotoId,
        String cargo,
        String igrejaSigla,
        UUID igrejaLogoId,
        String token,
        String refreshToken

) {

}
