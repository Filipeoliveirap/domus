package com.domus.api.modules.visitante.DTOs;

import com.domus.api.modules.visitante.Visitante;
import java.util.UUID;

public record VisitanteBuscaLeveResponse(UUID id, String nome, String telefone) {
    public static VisitanteBuscaLeveResponse from(Visitante v) {
        return new VisitanteBuscaLeveResponse(v.getId(), v.getNome(), v.getTelefone());
    }
}
