package com.domus.api.modules.inicio.dto;

import com.domus.api.modules.evento.DTOs.EventoResponse;
import com.domus.api.modules.evento.Evento;

import java.time.LocalDateTime;
import java.util.UUID;

/** Resumo de evento para as telas de início e dashboard. */
public record EventoResumoDTO(UUID id, String titulo, LocalDateTime inicio, String local,
                               EventoResponse.IgrejaResumo igrejaOrganizadora) {
    public static EventoResumoDTO from(Evento e) {
        return new EventoResumoDTO(e.getId(), e.getTitulo(), e.getInicioEm(), e.getLocalExibicao(),
                EventoResponse.IgrejaResumo.de(e.getIgreja()));
    }
}
