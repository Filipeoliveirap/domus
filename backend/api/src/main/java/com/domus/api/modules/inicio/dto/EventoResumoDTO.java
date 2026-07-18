package com.domus.api.modules.inicio.dto;

import com.domus.api.modules.evento.Evento;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resumo de evento para as telas de início e dashboard. */
public record EventoResumoDTO(UUID id, String titulo, LocalDateTime inicio, String local) {
    public static EventoResumoDTO from(Evento e) {
        return new EventoResumoDTO(e.getId(), e.getTitulo(), e.getInicioEm(), e.getLocal());
    }
}
