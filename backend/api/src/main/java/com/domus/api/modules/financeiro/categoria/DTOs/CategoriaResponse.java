package com.domus.api.modules.financeiro.categoria.DTOs;

import com.domus.api.modules.financeiro.categoria.CategoriaFinanceira;
import com.domus.api.modules.financeiro.categoria.TipoCategoria;

import java.util.UUID;

public record CategoriaResponse(
        UUID id,
        String nome,
        TipoCategoria tipo,
        boolean temVinculo
) {
    public static CategoriaResponse de(CategoriaFinanceira c) {
        return new CategoriaResponse(c.getId(), c.getNome(), c.getTipo(), false);
    }

    public static CategoriaResponse de(CategoriaFinanceira c, boolean temVinculo) {
        return new CategoriaResponse(c.getId(), c.getNome(), c.getTipo(), temVinculo);
    }
}
