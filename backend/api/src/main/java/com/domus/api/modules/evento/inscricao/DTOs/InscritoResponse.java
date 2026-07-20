package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Uma linha da lista de inscritos (ADMIN/LÍDER). */
public record InscritoResponse(
        UUID id,
        UUID membroId,
        String nome,
        String foto,
        /** NULL = a pessoa se inscreveu sozinha. */
        UUID inscritoPorUsuarioId,
        LocalDateTime inscritoEm,
        List<AcompanhanteResponse> acompanhantes
) {
    public static InscritoResponse from(InscricaoEvento i) {
        return new InscritoResponse(
                i.getId(),
                i.getMembro().getId(),
                i.getMembro().getNome(),
                i.getMembro().getFoto(),
                i.getInscritoPorUsuarioId(),
                i.getCreatedAt(),
                i.getAcompanhantes().stream().map(AcompanhanteResponse::from).toList()
        );
    }
}
