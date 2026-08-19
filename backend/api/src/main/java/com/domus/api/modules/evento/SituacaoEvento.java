package com.domus.api.modules.evento;

/** Situação DERIVADA de {@code inicioEm}/{@code fimEm}, calculada na hora — não é coluna no banco. */
public enum SituacaoEvento {
    /** Ainda não começou. */
    AGENDADO,
    /** Já começou e ainda não passou do fim (ou do fim do dia, se não houver fim declarado). */
    EM_ANDAMENTO,
    /** Já passou do fim (ou do fim do dia de início, quando não há fim declarado). */
    ENCERRADO
}
