package com.domus.api.modules.evento.DTOs;

/** {@code compareceram}/{@code percentualIgreja} são {@code null} (nunca zerados) quando controlaPresenca=false. */
public record RelatorioEventoResponse(
        Inscritos inscritos,
        /** % de pessoas CADASTRADAS da igreja (nunca convidados) que se inscreveram, sobre o total de pessoas ativas. */
        Double percentualIgrejaInscritos,
        Compareceram compareceram,
        Double percentualIgreja
) {
    public record Inscritos(long pessoas, long convidados) {}

    /** {@code pessoas}/{@code convidados} são "Pessoas da Igreja"/"Convidados" no front. */
    public record Compareceram(long pessoas, long convidados) {}
}
