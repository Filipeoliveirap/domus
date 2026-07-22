package com.domus.api.modules.evento.elegibilidade;

import com.domus.api.shared.exception.BusinessException;

import java.util.List;

/**
 * Carrega a lista de {@link Impedimento} até o {@code GlobalExceptionHandler}, que responde
 * 422 (não 400 genérico) — a inscrição não foi rejeitada por dado inválido, foi rejeitada
 * porque a PESSOA não é elegível para o evento. O front usa {@link #getImpedimentos()} para
 * mostrar exatamente o que barrou, com o mesmo código que o {@code GET .../elegibilidade}
 * já teria mostrado antes de tentar.
 */
public class NaoElegivelException extends BusinessException {

    private final List<Impedimento> impedimentos;

    public NaoElegivelException(List<Impedimento> impedimentos) {
        super("NAO_ELEGIVEL", mensagemDe(impedimentos));
        this.impedimentos = impedimentos;
    }

    public List<Impedimento> getImpedimentos() {
        return impedimentos;
    }

    private static String mensagemDe(List<Impedimento> impedimentos) {
        return impedimentos.stream()
                .map(Impedimento::mensagem)
                .reduce((a, b) -> a + " " + b)
                .orElse("Esta pessoa não é elegível para este evento.");
    }
}
