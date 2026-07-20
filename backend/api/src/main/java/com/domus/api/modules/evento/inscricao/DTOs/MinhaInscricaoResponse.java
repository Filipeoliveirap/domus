package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.util.List;
import java.util.UUID;

/** O que o próprio usuário vê sobre a sua inscrição no evento. */
public record MinhaInscricaoResponse(
        UUID id,
        boolean inscrito,
        List<AcompanhanteResponse> acompanhantes
) {
    public static MinhaInscricaoResponse from(InscricaoEvento i) {
        return new MinhaInscricaoResponse(
                i.getId(),
                i.estaConfirmada(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList()
        );
    }

    public static MinhaInscricaoResponse naoInscrito() {
        return new MinhaInscricaoResponse(null, false, List.of());
    }
}
