package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.postagem.ComentarioPostagem;

import java.time.LocalDateTime;
import java.util.UUID;

public record ComentarioResponse(
        UUID id,
        AutorResponse autor,
        IgrejaResumo igrejaAutor,
        String conteudo,
        UUID paiComentarioId,
        long totalCurtidas,
        boolean curtidoPorMim,
        boolean podeDeletar,
        LocalDateTime criadoEm
) {
    public static ComentarioResponse from(ComentarioPostagem c) {
        return from(c, 0, false, false);
    }

    public static ComentarioResponse from(ComentarioPostagem c, long totalCurtidas, boolean curtidoPorMim) {
        return from(c, totalCurtidas, curtidoPorMim, false);
    }

    public static ComentarioResponse from(ComentarioPostagem c, long totalCurtidas, boolean curtidoPorMim, boolean podeDeletar) {
        UUID paiId = c.getPaiComentario() != null ? c.getPaiComentario().getId() : null;
        IgrejaResumo igrejaAutor = c.getAutorPessoa() != null ? IgrejaResumo.de(c.getAutorPessoa().getIgreja()) : null;
        return new ComentarioResponse(
                c.getId(),
                AutorResponse.from(c.getAutorPessoa()),
                igrejaAutor,
                c.getConteudo(),
                paiId,
                totalCurtidas,
                curtidoPorMim,
                podeDeletar,
                c.getCriadoEm()
        );
    }
}
