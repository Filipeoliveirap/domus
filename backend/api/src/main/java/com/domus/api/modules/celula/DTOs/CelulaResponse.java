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
        int totalMembros,
        UUID fotoId,
        boolean souLiderDestaCelula,
        boolean temVinculo
) {
    public static CelulaResponse from(Celula celula) {
        return new CelulaResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), List.of(), 0,
                celula.getFoto() != null ? celula.getFoto().getId() : null, false, false);
    }

    public static CelulaResponse comResumo(Celula celula, List<CelulaMembro> membros, UUID pessoaLogadaId) {
        List<String> lideres = membros.stream()
                .filter(m -> m.getPapel() == PapelCelula.LIDER)
                .map(m -> m.getPessoa() != null ? m.getPessoa().getNome() : m.getVisitante().getNome())
                .toList();
        boolean souLider = pessoaLogadaId != null && membros.stream()
                .anyMatch(m -> m.getPapel() == PapelCelula.LIDER
                        && m.getPessoa() != null && pessoaLogadaId.equals(m.getPessoa().getId()));
        return new CelulaResponse(celula.getId(), celula.getNome(),
                celula.getDiaSemana(), celula.getHorario(), lideres, membros.size(),
                celula.getFoto() != null ? celula.getFoto().getId() : null, souLider,
                !membros.isEmpty());
    }
}
