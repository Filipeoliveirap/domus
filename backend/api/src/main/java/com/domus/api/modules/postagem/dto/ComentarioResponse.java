package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.postagem.ComentarioPostagem;

import java.time.LocalDateTime;
import java.util.UUID;

public record ComentarioResponse(
        UUID id,
        AutorResponse autor,
        String conteudo,
        LocalDateTime criadoEm
) {
    public static ComentarioResponse from(ComentarioPostagem c) {
        return new ComentarioResponse(
                c.getId(),
                AutorResponse.from(c.getAutorPessoa()),
                c.getConteudo(),
                c.getCriadoEm()
        );
    }
}
