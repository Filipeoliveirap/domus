package com.domus.api.modules.inicio.dto;

import java.util.List;
import java.util.UUID;

public record InicioResponse(
        List<Aniversariante> aniversariantesMes,
        List<EventoResumoDTO> proximosEventos
) {
    public record Aniversariante(UUID id, String nome, int dia) {}
}
