package com.domus.api.modules.evento.DTOs;

import java.math.BigDecimal;

/**
 * Prévia de quanto dinheiro/gente seria afetado ao apertar "Salvar" numa mudança de preço
 * — nunca grava nada, nem chama o Mercado Pago, só calcula pro admin decidir com o número
 * na mão antes de confirmar. {@code tipo == "SEM_IMPACTO"} quando não há mudança de preço
 * real, ou quando não há ninguém confirmado/aguardando pagamento pra afetar.
 *
 * <p>Campos de {@code PAGO_PARA_GRATUITO} (estorno) e de {@code GRATUITO_PARA_PAGO}
 * (cobrança nova) nunca vêm preenchidos ao mesmo tempo — cada mudança de preço só anda
 * numa direção por vez.</p>
 */
public record ImpactoMudancaPrecoResponse(
        String tipo,
        /** PAGO_PARA_GRATUITO: quem já pagou e seria estornado. */
        int pessoasComPagamentoPago,
        /** PAGO_PARA_GRATUITO: soma do que seria estornado de verdade no Mercado Pago. */
        BigDecimal valorTotalAEstornar,
        /** PAGO_PARA_GRATUITO: quem estava aguardando pagamento e seria confirmado direto. */
        int pessoasAguardandoPagamento,
        /** GRATUITO_PARA_PAGO: quantas pessoas já confirmadas ganhariam uma cobrança nova. */
        int pessoasSeraoCobradas,
        /** GRATUITO_PARA_PAGO: soma do que seria cobrado (pessoasSeraoCobradas × preço novo). */
        BigDecimal valorTotalACobrar
) {
    public static final String SEM_IMPACTO = "SEM_IMPACTO";
    public static final String PAGO_PARA_GRATUITO = "PAGO_PARA_GRATUITO";
    public static final String GRATUITO_PARA_PAGO = "GRATUITO_PARA_PAGO";

    public static ImpactoMudancaPrecoResponse semImpacto() {
        return new ImpactoMudancaPrecoResponse(SEM_IMPACTO, 0, BigDecimal.ZERO, 0, 0, BigDecimal.ZERO);
    }

    public static ImpactoMudancaPrecoResponse pagoParaGratuito(
            int pessoasComPagamentoPago, BigDecimal valorTotalAEstornar, int pessoasAguardandoPagamento) {
        return new ImpactoMudancaPrecoResponse(
                PAGO_PARA_GRATUITO, pessoasComPagamentoPago, valorTotalAEstornar, pessoasAguardandoPagamento,
                0, BigDecimal.ZERO);
    }

    public static ImpactoMudancaPrecoResponse gratuitoParaPago(int pessoasSeraoCobradas, BigDecimal valorTotalACobrar) {
        return new ImpactoMudancaPrecoResponse(
                GRATUITO_PARA_PAGO, 0, BigDecimal.ZERO, 0, pessoasSeraoCobradas, valorTotalACobrar);
    }
}
