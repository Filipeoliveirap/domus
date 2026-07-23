package com.domus.api.modules.evento.elegibilidade;

import java.util.List;

public record Elegibilidade(boolean apto, List<Impedimento> impedimentos) {

    public static Elegibilidade aprovado() { return new Elegibilidade(true, List.of()); }

    public List<Impedimento> impedimentosNaoContornaveis() {
        return impedimentos.stream().filter(i -> !i.contornavel()).toList();
    }

    /** Quem gerencia consegue passar por cima de TODOS os impedimentos presentes? */
    public boolean totalmenteContornavel() {
        return !apto && impedimentosNaoContornaveis().isEmpty();
    }
}
