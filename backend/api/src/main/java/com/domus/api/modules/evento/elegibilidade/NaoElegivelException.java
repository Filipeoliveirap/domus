package com.domus.api.modules.evento.elegibilidade;

import com.domus.api.shared.exception.BusinessException;

import java.util.List;

/** Exceção de negócio usada quando uma pessoa não atende aos requisitos de elegibilidade do evento. */
public class NaoElegivelException extends BusinessException {

    private static final String MENSAGEM_GENERICA = "Esta pessoa não atende aos requisitos deste evento.";

    private final List<Impedimento> impedimentos;

    public NaoElegivelException(List<Impedimento> impedimentos) {
        super("NAO_ELEGIVEL", mensagemDe(impedimentos));
        this.impedimentos = impedimentos;
    }

    private NaoElegivelException(List<Impedimento> impedimentos, String mensagem) {
        super("NAO_ELEGIVEL", mensagem);
        this.impedimentos = impedimentos;
    }

    /** Se {@code podeVerDetalhes} for false, sanitiza mensagens e impedimentos para quem não gerencia inscrições. */
    public static NaoElegivelException para(List<Impedimento> impedimentos, boolean podeVerDetalhes) {
        if (podeVerDetalhes) {
            return new NaoElegivelException(impedimentos);
        }
        List<Impedimento> genericos = impedimentos.stream()
                .map(i -> new Impedimento(i.codigo(), MENSAGEM_GENERICA, i.contornavel()))
                .toList();
        return new NaoElegivelException(genericos, MENSAGEM_GENERICA);
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
