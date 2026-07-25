package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.Celula;
import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.DiaSemana;
import com.domus.api.modules.celula.PapelCelula;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record CelulaResponse(
        UUID id,
        String nome,
        DiaSemana diaSemana,
        LocalTime horario,
        List<String> lideres,
        int totalMembros
) {
    public static CelulaResponse from(Celula celula) {
        return new CelulaResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), List.of(), 0);
    }

    public static CelulaResponse comResumo(Celula celula, List<CelulaMembro> membros) {
        List<String> lideres = membros.stream()
                .filter(m -> m.getPapel() == PapelCelula.LIDER)
                .map(m -> m.getPessoa() != null ? m.getPessoa().getNome() : m.getVisitante().getNome())
                .toList();
        return new CelulaResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), lideres, membros.size());
    }
}
