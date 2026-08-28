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
        /** PAGO_PARA_GRATUITO/VALOR_DIMINUIU: quem já pagou e seria (parcialmente) estornado. */
        int pessoasComPagamentoPago,
        /** PAGO_PARA_GRATUITO/VALOR_DIMINUIU: soma do que seria estornado de verdade no Mercado Pago. */
        BigDecimal valorTotalAEstornar,
        /** PAGO_PARA_GRATUITO: quem estava aguardando pagamento e seria confirmado direto.
         *  VALOR_AUMENTOU/VALOR_DIMINUIU: quem está aguardando pagamento e só teria o valor
         *  da cobrança em aberto atualizado (sem mudar de status nem gerar cobrança nova). */
        int pessoasAguardandoPagamento,
        /** GRATUITO_PARA_PAGO/VALOR_AUMENTOU: quantas pessoas já confirmadas ganhariam uma
         *  cobrança nova (do valor cheio, ou só da diferença em VALOR_AUMENTOU). */
        int pessoasSeraoCobradas,
        /** GRATUITO_PARA_PAGO/VALOR_AUMENTOU: soma do que seria cobrado. */
        BigDecimal valorTotalACobrar
) {
    public static final String SEM_IMPACTO = "SEM_IMPACTO";
    public static final String PAGO_PARA_GRATUITO = "PAGO_PARA_GRATUITO";
    public static final String GRATUITO_PARA_PAGO = "GRATUITO_PARA_PAGO";
    /** Evento continua pago, só o valor mudou pra cima — quem já pagou o valor antigo
     *  recebe uma cobrança nova só da diferença (sem perder a vaga/confirmação). */
    public static final String VALOR_AUMENTOU = "VALOR_AUMENTOU";
    /** Evento continua pago, só o valor mudou pra baixo — quem já pagou o valor antigo
     *  recebe o excedente estornado automaticamente. */
    public static final String VALOR_DIMINUIU = "VALOR_DIMINUIU";
    /** Achado ao vivo (2026-08-27): quando o evento já passou por reajustes diferentes
     *  pra pessoas diferentes antes, um novo reajuste pode fazer ALGUMAS pessoas deverem
     *  mais e OUTRAS precisarem de estorno ao mesmo tempo — os dois grupos de campos vêm
     *  preenchidos juntos aqui (é a única exceção à regra "nunca as duas direções ao mesmo
     *  tempo" dos outros tipos). */
    public static final String VALOR_MISTO = "VALOR_MISTO";

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

    public static ImpactoMudancaPrecoResponse valorAumentou(
            int pessoasSeraoCobradas, BigDecimal valorTotalACobrar, int pessoasAguardandoPagamento) {
        return new ImpactoMudancaPrecoResponse(
                VALOR_AUMENTOU, 0, BigDecimal.ZERO, pessoasAguardandoPagamento,
                pessoasSeraoCobradas, valorTotalACobrar);
    }

    public static ImpactoMudancaPrecoResponse valorDiminuiu(
            int pessoasComPagamentoPago, BigDecimal valorTotalAEstornar, int pessoasAguardandoPagamento) {
        return new ImpactoMudancaPrecoResponse(
                VALOR_DIMINUIU, pessoasComPagamentoPago, valorTotalAEstornar, pessoasAguardandoPagamento,
                0, BigDecimal.ZERO);
    }

    public static ImpactoMudancaPrecoResponse valorMisto(
            int pessoasComPagamentoPago, BigDecimal valorTotalAEstornar,
            int pessoasSeraoCobradas, BigDecimal valorTotalACobrar, int pessoasAguardandoPagamento) {
        return new ImpactoMudancaPrecoResponse(
                VALOR_MISTO, pessoasComPagamentoPago, valorTotalAEstornar, pessoasAguardandoPagamento,
                pessoasSeraoCobradas, valorTotalACobrar);
    }
}
