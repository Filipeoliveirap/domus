package com.domus.api.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp,
    Map<String, String> campos
) {
    public static ErrorResponse of(int status, String erro, String mensagem) {
        return new ErrorResponse(status, erro, mensagem, LocalDateTime.now(), null);
    }
    public static ErrorResponse ofValidacao(Map<String, String> campos) {
        return new ErrorResponse(
                400,
                "ERRO_VALIDACAO",
                "Um ou mais campos são inválidos",
                LocalDateTime.now(),
                campos
        );
    }

}
