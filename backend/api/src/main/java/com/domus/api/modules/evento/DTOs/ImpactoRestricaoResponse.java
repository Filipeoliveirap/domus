package com.domus.api.modules.evento.DTOs;

import java.util.List;
import java.util.UUID;

/** Prévia de quem ficaria de fora ao apertar restrição — nunca gravada, só devolvida pro admin decidir. */
public record ImpactoRestricaoResponse(List<InscritoImpactado> afetados) {

    /** {@code motivos} vem de {@code Impedimento.mensagem()} — o mesmo texto do 422 de elegibilidade. */
    public record InscritoImpactado(UUID pessoaId, String nome, List<String> motivos) {}
}
