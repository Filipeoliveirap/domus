package com.domus.api.shared.exception;

public class ContaBloqueadaException extends RuntimeException {
    public ContaBloqueadaException(long minutosRestantes) {
        super("Conta temporariamente bloqueada por excesso de tentativas. " +
                "Tente novamente em " + minutosRestantes + " minuto(s).");
    }
}