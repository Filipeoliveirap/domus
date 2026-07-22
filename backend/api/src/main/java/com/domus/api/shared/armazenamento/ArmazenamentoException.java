package com.domus.api.shared.armazenamento;

public class ArmazenamentoException extends RuntimeException {
    public ArmazenamentoException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
