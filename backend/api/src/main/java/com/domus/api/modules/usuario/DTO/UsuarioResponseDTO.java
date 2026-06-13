package com.domus.api.modules.usuario.DTO;


import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String role,
        LocalDateTime criadoEm
) {
}
