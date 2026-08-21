package com.domus.api.modules.evento.DTOs;

import com.domus.api.modules.evento.Evento;

import java.time.LocalDateTime;
import java.util.UUID;

/** DTO enxuto pra tela de Arquivados — sem os campos de local/responsável/elegibilidade de {@link EventoResponse}. */
public record EventoArquivadoResponse(
        UUID id,
        String titulo,
        LocalDateTime inicioEm,
        String tipo,
        boolean temVinculo,
        long totalInscritos,
        UUID serieId
) {
    public static EventoArquivadoResponse de(Evento e, long totalInscritos) {
        return new EventoArquivadoResponse(
                e.getId(), e.getTitulo(), e.getInicioEm(), e.getTipo(),
                totalInscritos > 0, totalInscritos,
                e.getSerie() != null ? e.getSerie().getId() : null
        );
    }
}
