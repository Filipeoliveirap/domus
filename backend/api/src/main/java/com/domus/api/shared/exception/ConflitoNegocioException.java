package com.domus.api.shared.exception;

/** Recurso existe e dado é válido, mas o estado atual torna a operação sem sentido — vira HTTP 409. */
public class ConflitoNegocioException extends RuntimeException {
    private final String codigo;

    public ConflitoNegocioException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
