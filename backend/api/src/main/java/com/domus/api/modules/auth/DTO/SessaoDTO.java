package com.domus.api.modules.auth.DTO;

import java.util.UUID;

/**
 * O que o front precisa saber sobre a sessão — e nada além disso.
 *
 * <p>Não carrega token: os tokens viajam em cookie httpOnly e o JavaScript
 * nunca os vê. Este é o corpo de /auth/login, /auth/google/* e /auth/me.
 */
public record SessaoDTO(
        UUID id,
        String nome,
        String role,
        UUID igrejaId,
        String igrejaNome
) {
}
