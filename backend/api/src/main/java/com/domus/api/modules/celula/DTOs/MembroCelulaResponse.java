package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.CelulaMembro;
import com.domus.api.modules.celula.PapelCelula;

import java.util.UUID;

public record MembroCelulaResponse(
        UUID id,
        String tipo,
        UUID pessoaId,
        UUID visitanteId,
        String nome,
        UUID fotoId,
        PapelCelula papel
) {
    public static MembroCelulaResponse from(CelulaMembro m) {
        String tipo = m.getPessoa() != null ? "PESSOA" : "VISITANTE";
        String nome = m.getPessoa() != null ? m.getPessoa().getNome() : m.getVisitante().getNome();
        UUID pessoaId = m.getPessoa() != null ? m.getPessoa().getId() : null;
        UUID visitanteId = m.getVisitante() != null ? m.getVisitante().getId() : null;
        UUID fotoId = m.getPessoa() != null && m.getPessoa().getFoto() != null
                ? m.getPessoa().getFoto().getId() : null;
        return new MembroCelulaResponse(m.getId(), tipo, pessoaId, visitanteId, nome, fotoId, m.getPapel());
    }
}
