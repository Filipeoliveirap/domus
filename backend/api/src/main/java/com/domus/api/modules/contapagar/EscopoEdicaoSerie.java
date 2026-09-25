package com.domus.api.modules.contapagar;

/** Escopo de edição/exclusão em uma conta recorrente. */
public enum EscopoEdicaoSerie {
    /** Apenas esta ocorrência. */
    ESTA,
    /** Esta ocorrência e todas as seguintes já materializadas. */
    ESTA_E_SEGUINTES,
    /** Toda a série (gera nova + exclui todas). */
    SERIE
}
