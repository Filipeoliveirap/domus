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
        String igrejaNome,
        /** Foto da PESSOA vinculada (Usuario não tem foto própria). Null = sem foto. */
        UUID fotoId,
        /** Cargo da pessoa (Pastor, Missionário…) — exibido na sidebar no lugar da role. */
        String cargo,
        /** Sigla da igreja (IBC, SIBAPI…) — exibida no header do TopBar. */
        String igrejaSigla,
        /** Logo da igreja — exibida no ícone do TopBar no lugar do Church quando existe. */
        UUID igrejaLogoId
) {
}
