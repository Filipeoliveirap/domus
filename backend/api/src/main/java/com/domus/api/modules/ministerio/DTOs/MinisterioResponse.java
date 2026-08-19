package com.domus.api.modules.ministerio.DTOs;

import com.domus.api.modules.ministerio.Ministerio;
import com.domus.api.modules.ministerio.MinisterioMembro;
import com.domus.api.modules.ministerio.Papel;
import java.util.List;
import java.util.UUID;

public record MinisterioResponse(UUID id, String nome, UUID fotoId, List<String> lideres, int totalMembros,
                                  boolean temVinculo) {
    /** Usado onde só o cadastro básico importa (ex.: `GET /pessoas/{id}/ministerios`) — sem
     * consultar membros, então líderes/contagem vêm zerados. */
    public static MinisterioResponse from(Ministerio ministerio) {
        return new MinisterioResponse(ministerio.getId(), ministerio.getNome(),
                ministerio.getFoto() != null ? ministerio.getFoto().getId() : null,
                List.of(), 0, false);
    }

    /** Usado na listagem (`GET /ministerios`), onde o card mostra líder(es) e quantidade de
     * membros — resumo visual pedido no mockup do Stitch (sem descrição nem frequência: fora
     * do escopo do cadastro, que é só nome). */
    public static MinisterioResponse comResumo(Ministerio ministerio, List<MinisterioMembro> membrosAtivos) {
        List<String> lideres = membrosAtivos.stream()
                .filter(m -> m.getPapel() == Papel.LIDER)
                .map(m -> m.getPessoa().getNome())
                .toList();
        return new MinisterioResponse(ministerio.getId(), ministerio.getNome(),
                ministerio.getFoto() != null ? ministerio.getFoto().getId() : null,
                lideres, membrosAtivos.size(), !membrosAtivos.isEmpty());
    }
}
