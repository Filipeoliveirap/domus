package com.domus.api.modules.evento.DTOs;

import java.math.BigDecimal;

/**
 * Prévia de quanto dinheiro/gente seria afetado ao apertar "Salvar" numa mudança de preço
 * — nunca grava nada, só calcula pro admin decidir com o número na mão antes de confirmar
 * um estorno de verdade. {@code tipo == "SEM_IMPACTO"} quando não há mudança de preço, ou
 * quando não há ninguém confirmado/aguardando pagamento pra afetar.
 */
public record ImpactoMudancaPrecoResponse(
        String tipo,
        int pessoasComPagamentoPago,
        BigDecimal valorTotalAEstornar,
        int pessoasAguardandoPagamento
) {
    public static final String SEM_IMPACTO = "SEM_IMPACTO";
    public static final String PAGO_PARA_GRATUITO = "PAGO_PARA_GRATUITO";

    public static ImpactoMudancaPrecoResponse semImpacto() {
        return new ImpactoMudancaPrecoResponse(SEM_IMPACTO, 0, BigDecimal.ZERO, 0);
    }
}
