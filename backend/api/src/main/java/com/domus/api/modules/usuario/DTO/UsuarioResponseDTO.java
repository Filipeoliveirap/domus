package com.domus.api.modules.usuario.DTO;


import com.domus.api.modules.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String role,
        boolean ativo,
        LocalDateTime ultimoLoginEm,
        // Convite ainda não aceito: usuário criado por convite que nunca fez login
        // (nem nativo nem Google). Derivado de ultimoLoginEm == null.
        boolean convitePendente,
        LocalDateTime criadoEm
) {
    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole().getNome(),
                u.isAtivo(),
                u.getUltimoLoginEm(),
                u.getUltimoLoginEm() == null,
                u.getCreatedAt()
        );
    }
}
