package com.domus.api.modules.evento.DTOs;

/**
 * Relatório individual de um evento (modal de detalhe). {@code compareceram} e
 * {@code percentualIgreja} são {@code null} quando {@code evento.controlaPresenca=false} —
 * a seção some inteira no front, nunca aparece zerada (ver EventoRelatorioService).
 */
public record RelatorioEventoResponse(
        Inscritos inscritos,
        Compareceram compareceram,
        Double percentualIgreja
) {
    public record Inscritos(long pessoas, long convidados) {}

    /** {@code pessoas}/{@code convidados} são "Pessoas da Igreja"/"Convidados" no front. */
    public record Compareceram(long pessoas, long convidados) {}
}
