package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.SituacaoEvento;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoResponse(
        UUID id,
        String titulo,
        String descricao,
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        String local,
        UUID fotoId,
        LocalDateTime createdAt,
        Integer vagas,
        java.math.BigDecimal preco,
        boolean exclusivoMembros,
        boolean requerInscricao,
        SituacaoEvento situacao,
        /**
         * Só populado pela edição que ligou {@code exclusivoMembros} e removeu automaticamente
         * quem não se qualifica mais (ver B4). {@code null} em toda outra resposta — campo
         * aditivo, não quebra quem já consome {@code EventoResponse}.
         */
        Integer inscricoesRemovidas
) {
    public static EventoResponse from(Evento e) {
        return from(e, null);
    }

    public static EventoResponse from(Evento e, Integer inscricoesRemovidas) {
        return new EventoResponse(
                e.getId(), e.getTitulo(), e.getDescricao(),
                e.getInicioEm(), e.getFimEm(), e.getLocal(),
                e.getFoto() != null ? e.getFoto().getId() : null, e.getCreatedAt(),
                e.getVagas(), e.getPreco(), e.isExclusivoMembros(),
                e.isRequerInscricao(), e.getSituacao(), inscricoesRemovidas
        );
    }
}
