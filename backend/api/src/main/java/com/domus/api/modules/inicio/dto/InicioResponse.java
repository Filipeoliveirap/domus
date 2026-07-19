package com.domus.api.modules.inicio.dto;

import java.util.List;
import java.util.UUID;

public record InicioResponse(
        List<Aniversariante> aniversariantesMes,
        List<EventoResumoDTO> proximosEventos
) {
    /** {@code foto} é nulo enquanto o upload (Fase 2) não existir — a tela cai nas iniciais. */
    public record Aniversariante(UUID id, String nome, int dia, String foto) {}
}
