package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.DiaSemana;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CelulaDetalheResponse(
        UUID id,
        String nome,
        DiaSemana diaSemana,
        LocalTime horario,
        List<MembroCelulaResponse> membros,
        boolean souLiderDestaCelula
) {
    public static CelulaDetalheResponse from(Celula celula, List<MembroCelulaResponse> membros,
                                              boolean souLider) {
        return new CelulaDetalheResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), membros, souLider);
    }
}
