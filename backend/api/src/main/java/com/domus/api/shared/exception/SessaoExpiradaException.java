package com.domus.api.shared.exception;

/** Sessão inexistente/expirada/revogada — vira 401, não 400, porque o front chaveia refresh/logout nesse código. */
public class SessaoExpiradaException extends RuntimeException {

    private final String codigo;

    public SessaoExpiradaException(String codigo, String message) {
        super(message);
        this.codigo = codigo;
    }

    public String getCodigo() {
        return codigo;
    }
}
