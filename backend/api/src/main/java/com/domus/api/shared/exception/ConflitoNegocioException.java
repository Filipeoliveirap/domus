package com.domus.api.shared.exception;

/**
 * "Esta ação não se aplica ao estado atual do recurso" — vira HTTP 409 Conflict.
 *
 * <p>Diferente de {@link BusinessException} (400, dado inválido) e de
 * {@link ResourceNotFoundException} (404, recurso não existe): aqui o recurso existe e o
 * dado é válido, mas o estado atual (ex.: {@code evento.controlaPresenca=false}) torna a
 * operação sem sentido — marcar presença onde não há lista de presença para marcar.
 */
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
