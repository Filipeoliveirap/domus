package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.pessoa.Pessoa;
import java.util.UUID;

public record AutorResponse(
        UUID id,
        String nome,
        UUID fotoId,
        String cargo
) {
    public static AutorResponse from(Pessoa pessoa) {
        if (pessoa == null) return null;
        UUID fotoId = pessoa.getFoto() != null ? pessoa.getFoto().getId() : null;
        String cargo = pessoa.getCargo() != null ? pessoa.getCargo().getNome() : null;
        return new AutorResponse(pessoa.getId(), pessoa.getNome(), fotoId, cargo);
    }
}
