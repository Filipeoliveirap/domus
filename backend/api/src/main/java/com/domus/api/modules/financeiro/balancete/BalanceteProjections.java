package com.domus.api.modules.financeiro.balancete;

import java.math.BigDecimal;
import java.util.UUID;

public interface BalanceteProjections {

    interface LinhaMensalAgregada {
        UUID getCategoriaId();
        String getNomeCategoria();
        Boolean getArquivada();
        String getTipo();
        Integer getMes();
        BigDecimal getTotal();
    }

    interface LinhaMensalConsolidada {
        String getChave();          // unaccent(lower(nome)) — casa categorias entre igrejas
        String getNomeExibicao();
        String getTipo();
        Integer getMes();
        BigDecimal getTotal();
    }
}
