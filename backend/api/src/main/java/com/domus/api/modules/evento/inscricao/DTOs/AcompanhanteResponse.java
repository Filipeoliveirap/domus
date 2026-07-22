package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.AcompanhanteInscricao;
import java.util.UUID;

public record AcompanhanteResponse(UUID id, String nome, String telefone) {
    public static AcompanhanteResponse from(AcompanhanteInscricao a) {
        return new AcompanhanteResponse(a.getId(), a.getNome(), a.getTelefone());
    }
}
