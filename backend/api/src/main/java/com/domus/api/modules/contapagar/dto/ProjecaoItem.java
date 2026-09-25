package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.contapagar.ContaAPagar;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Item de uma projeção de série de conta. */
public record ProjecaoItem(
    UUID ocorrenciaId,
    LocalDate vencimento,
    BigDecimal valor,
    String status,
    Boolean materializada
) {
    public static ProjecaoItem from(ContaAPagar conta) {
        return new ProjecaoItem(
            conta.getId(),
            conta.getVencimento(),
            conta.getValor(),
            conta.getStatus().name(),
            true
        );
    }

    /** Prevista (não materializada). */
    public static ProjecaoItem prevista(LocalDate vencimento, BigDecimal valor) {
        return new ProjecaoItem(null, vencimento, valor, null, false);
    }
}
