package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.MinisterioMembro;
import com.domus.api.modules.ministerio.Papel;
import java.util.UUID;

public record MembroResponse(UUID pessoaId, String nome, UUID fotoId, Papel papel) {
    public static MembroResponse from(MinisterioMembro membro) {
        var pessoa = membro.getPessoa();
        UUID fotoId = pessoa.getFoto() != null ? pessoa.getFoto().getId() : null;
        return new MembroResponse(pessoa.getId(), pessoa.getNome(), fotoId, membro.getPapel());
    }
}
