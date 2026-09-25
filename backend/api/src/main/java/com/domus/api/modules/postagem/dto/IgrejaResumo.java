package com.domus.api.modules.postagem.dto;

import com.domus.api.modules.igreja.Igreja;
import java.util.UUID;

public record IgrejaResumo(
        UUID id,
        String nome,
        String sigla
) {
    public static IgrejaResumo de(Igreja igreja) {
        if (igreja == null) return null;
        return new IgrejaResumo(igreja.getId(), igreja.getNome(), igreja.getSigla());
    }
}
