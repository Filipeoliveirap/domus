package com.domus.api.modules.usuario.DTO;


import com.domus.api.modules.usuario.Usuario;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String role,
        boolean ativo,
        LocalDateTime ultimoLoginEm,
        boolean convitePendente,
        LocalDateTime criadoEm,
        UUID fotoId,
        List<String> capacidadesExtras
) {
    public UsuarioResponseDTO(UUID id, String nome, String email, String role, boolean ativo,
                              LocalDateTime ultimoLoginEm, LocalDateTime criadoEm, UUID fotoId) {
        this(id, nome, email, role, ativo, ultimoLoginEm, ultimoLoginEm == null, criadoEm, fotoId, List.of());
    }

    public static UsuarioResponseDTO from(Usuario u) {
        return new UsuarioResponseDTO(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getRole().getNome(),
                u.isAtivo(),
                u.getUltimoLoginEm(),
                u.getUltimoLoginEm() == null,
                u.getCreatedAt(),
                u.getPessoa().getFoto() != null ? u.getPessoa().getFoto().getId() : null,
                List.of()
        );
    }
}
