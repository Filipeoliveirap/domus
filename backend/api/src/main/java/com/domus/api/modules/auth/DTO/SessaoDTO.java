package com.domus.api.modules.auth.DTO;

import java.util.List;
import java.util.UUID;

/** Não carrega token — tokens viajam em cookie httpOnly, JS nunca os vê. */
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
        UUID igrejaLogoId,
        /** Capacidades extras acumuladas (SECRETARIO, TESOUREIRO). */
        List<String> capacidadesExtras,
        /** true = precisa (re)aceitar Termos/Política (conta nova ou versão desatualizada). */
        boolean precisaAceitarTermos,
        /** Data do último aceite (independente da versão) — exibida no perfil. */
        java.time.LocalDateTime termosAceitosEm
) {
    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                List.of(), false, null);
    }

    public SessaoDTO(UUID id, String nome, String role, UUID igrejaId, String igrejaNome,
                      UUID fotoId, String cargo, String igrejaSigla, UUID igrejaLogoId,
                      List<String> capacidadesExtras) {
        this(id, nome, role, igrejaId, igrejaNome, fotoId, cargo, igrejaSigla, igrejaLogoId,
                capacidadesExtras, false, null);
    }
}
