package com.domus.api.modules.evento.DTOs;

import java.util.List;
import java.util.UUID;

/**
 * Prévia de quem ficaria de fora ao apertar/ligar uma restrição de elegibilidade num evento que
 * já tem inscritos — nunca é gravada, só devolvida para o admin decidir (ver Task 6, decisão 6:
 * cancelar sozinho apagaria as exceções deliberadas que o próprio admin criou com o "inscrever
 * mesmo assim").
 */
public record ImpactoRestricaoResponse(List<InscritoImpactado> afetados) {

    /** {@code motivos} vem de {@code Impedimento.mensagem()} — o mesmo texto do 422 de elegibilidade. */
    public record InscritoImpactado(UUID pessoaId, String nome, List<String> motivos) {}
}
