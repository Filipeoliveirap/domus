package com.domus.api.shared.exception;

/**
 * Versão genérica, sem dependência de módulo, de {@code Impedimento}
 * (ver {@code com.domus.api.modules.evento.elegibilidade.Impedimento}). {@code shared} não
 * pode importar de uma feature — isso inverteria a dependência e faria {@code shared} deixar
 * de compilar sem o módulo {@code evento}. O mapeamento {@code Impedimento -> DetalheErro} é
 * feito no {@link GlobalExceptionHandler}, ponto que já conhece os dois lados.
 *
 * @param codigo      mesmo código do impedimento de origem (ex.: {@code FAIXA_ETARIA}).
 * @param mensagem     mensagem já pronta para exibição (detalhada ou genérica — a decisão de
 *                     qual delas fica em quem monta o impedimento, não aqui).
 * @param contornavel  quem gerencia pode inscrever assim mesmo.
 */
public record DetalheErro(String codigo, String mensagem, boolean contornavel) {}
