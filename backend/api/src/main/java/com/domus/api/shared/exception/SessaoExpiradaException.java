package com.domus.api.shared.exception;

/**
 * Sessão inexistente, expirada ou revogada — vira HTTP 401.
 *
 * <p>Existe separada de {@link BusinessException} (que vira 400) porque "sessão morta" não é
 * erro de regra de negócio: é falta de autenticação, e 401 é o código que diz isso. O front
 * inteiro chaveia o fluxo de refresh/logout em 401, então devolver 400 aqui faria um logout
 * legítimo parecer bug de validação.
 */
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
