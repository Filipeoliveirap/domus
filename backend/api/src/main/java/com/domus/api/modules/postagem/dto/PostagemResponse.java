package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.postagem.Postagem;
import com.domus.api.modules.postagem.TipoPostagem;
import com.domus.api.modules.postagem.TipoReacao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record PostagemResponse(
        UUID id,
        AutorResponse autor,
        TipoPostagem tipo,
        boolean oficial,
        String titulo,
        String conteudo,
        UUID fotoId,
        String versiculoRef,
        boolean fixado,
        LocalDateTime criadoEm,
        long totalCurtidas,
        long totalComentarios,
        TipoReacao minhaReacao,
        List<ComentarioResponse> comentariosRecentes
) {
    public static PostagemResponse from(
            Postagem p,
            long totalCurtidas,
            long totalComentarios,
            TipoReacao minhaReacao,
            List<ComentarioResponse> comentariosRecentes
    ) {
        UUID fotoId = p.getFoto() != null ? p.getFoto().getId() : null;
        return new PostagemResponse(
                p.getId(),
                AutorResponse.from(p.getAutorPessoa()),
                p.getTipo(),
                p.isOficial(),
                p.getTitulo(),
                p.getConteudo(),
                fotoId,
                p.getVersiculoRef(),
                p.isFixado(),
                p.getCriadoEm(),
                totalCurtidas,
                totalComentarios,
                minhaReacao,
                comentariosRecentes
        );
    }
}
