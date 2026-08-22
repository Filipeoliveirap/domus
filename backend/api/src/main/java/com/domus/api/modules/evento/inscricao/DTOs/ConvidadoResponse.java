package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import java.util.UUID;

public record ConvidadoResponse(UUID inscricaoId, String nome, String telefone) {
    public static ConvidadoResponse from(InscricaoEvento i) {
        return new ConvidadoResponse(i.getId(), i.getNomeConvidado(), i.getTelefoneConvidado());
    }
}
