package com.domus.api.modules.contapagar;

/** Frequência de recorrência de uma conta a pagar. */
public enum RecorrenciaFrequencia {
    MENSAL(1),
    TRIMESTRAL(3),
    SEMESTRAL(6),
    ANUAL(12);

    private final int meses;

    RecorrenciaFrequencia(int meses) {
        this.meses = meses;
    }

    /** Multiplicador de meses — usado para calcular próxima data de vencimento. */
    public int meses() {
        return meses;
    }
}
