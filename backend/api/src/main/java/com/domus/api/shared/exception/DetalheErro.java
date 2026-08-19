package com.domus.api.shared.exception;

/**
 * Versão genérica de {@code Impedimento}, sem dependência de módulo — {@code shared} não pode
 * importar de uma feature. Mapeamento feito em {@link GlobalExceptionHandler}.
 *
 * @param contornavel quem gerencia pode inscrever assim mesmo.
 */
public record DetalheErro(String codigo, String mensagem, boolean contornavel) {}
