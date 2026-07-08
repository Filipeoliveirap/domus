package com.domus.api.shared.DTO;

import com.domus.api.modules.outbox.TipoEntidadeOutbox;

public record ResultadoBusca(
        String id,
        TipoEntidadeOutbox tipo,
        String titulo,
        String subtitulo
) {}
