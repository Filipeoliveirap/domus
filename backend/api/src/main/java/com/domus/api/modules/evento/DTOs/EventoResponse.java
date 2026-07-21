package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.Evento;
import java.time.LocalDateTime;
import java.util.UUID;

public record EventoResponse(
        UUID id,
        String titulo,
        String descricao,
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        String local,
        String foto,
        LocalDateTime createdAt,
        Integer vagas,
        java.math.BigDecimal preco,
        boolean exclusivoMembros,
        boolean exclusivoBatizados
) {
    public static EventoResponse from(Evento e) {
        return new EventoResponse(
                e.getId(), e.getTitulo(), e.getDescricao(),
                e.getInicioEm(), e.getFimEm(), e.getLocal(),
                e.getFoto(), e.getCreatedAt(),
                e.getVagas(), e.getPreco(), e.isExclusivoMembros(), e.isExclusivoBatizados()
        );
    }
}
