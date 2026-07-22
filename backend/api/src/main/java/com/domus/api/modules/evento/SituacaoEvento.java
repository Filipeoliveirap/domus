package com.domus.api.modules.evento;

/**
 * Situação do evento no tempo, DERIVADA de {@code inicioEm}/{@code fimEm} — não é coluna
 * no banco. Dado derivável que vira coluna fica velho sem um scheduler para atualizá-lo;
 * calculando na hora (ver {@link Evento#getSituacao()}), a situação está sempre certa e
 * nunca pode dessincronizar do relógio.
 */
public enum SituacaoEvento {
    /** Ainda não começou. */
    AGENDADO,
    /** Já começou e ainda não passou do fim (ou do fim do dia, se não houver fim declarado). */
    EM_ANDAMENTO,
    /** Já passou do fim (ou do fim do dia de início, quando não há fim declarado). */
    ENCERRADO
}
