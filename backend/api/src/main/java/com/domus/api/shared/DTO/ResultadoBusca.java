package com.domus.api.shared.DTO;

import com.domus.api.modules.outbox.TipoEntidadeOutbox;

public record ResultadoBusca(
        String id,
        TipoEntidadeOutbox tipo,
        String titulo,
        String subtitulo,
        // Só usado por VISITANTE: quando preenchido, o front navega pra célula (com o
        // visitante em destaque) em vez da lista de visitantes, que não o lista mais.
        String celulaId
) {
    public ResultadoBusca(String id, TipoEntidadeOutbox tipo, String titulo, String subtitulo) {
        this(id, tipo, titulo, subtitulo, null);
    }
}
