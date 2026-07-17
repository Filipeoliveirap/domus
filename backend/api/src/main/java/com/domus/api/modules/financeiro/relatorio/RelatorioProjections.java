package com.domus.api.modules.financeiro.relatorio;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public interface RelatorioProjections {

    interface ResumoAgregado {
        BigDecimal getTotalEntradas();
        BigDecimal getTotalSaidas();
        Long getQuantidade();
    }

    interface CategoriaAgregada {
        UUID getCategoriaId();
        String getCategoriaNome();
        TipoMovimentacao getTipo();
        BigDecimal getTotal();
    }

    interface MesAgregado {
        Integer getAno();
        Integer getMes();
        BigDecimal getEntradas();
        BigDecimal getSaidas();
    }

    interface MaiorLancamento {
        UUID getId();
        String getDescricao();
        String getCategoriaNome();
        TipoMovimentacao getTipo();
        BigDecimal getValor();
        LocalDate getDataMovimentacao();
    }
}