package com.domus.api.shared.exception;

import com.domus.api.modules.evento.elegibilidade.Impedimento;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    int status,
    String error,
    String message,
    LocalDateTime timestamp,
    Map<String, String> campos,
    List<Impedimento> impedimentos
) {
    public static ErrorResponse of(int status, String erro, String mensagem) {
        return new ErrorResponse(status, erro, mensagem, LocalDateTime.now(), null, null);
    }
    public static ErrorResponse ofValidacao(Map<String, String> campos) {
        return new ErrorResponse(
                400,
                "ERRO_VALIDACAO",
                "Um ou mais campos são inválidos",
                LocalDateTime.now(),
                campos,
                null
        );
    }

    // 422 de NAO_ELEGIVEL: além do código/mensagem de sempre, carrega a lista de
    // impedimentos — é o que permite o front mostrar CADA restrição, não só uma frase.
    public static ErrorResponse ofElegibilidade(String codigo, String mensagem, List<Impedimento> impedimentos) {
        return new ErrorResponse(422, codigo, mensagem, LocalDateTime.now(), null, impedimentos);
    }

}
