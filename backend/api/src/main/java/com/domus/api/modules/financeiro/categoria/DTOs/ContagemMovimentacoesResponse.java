package com.domus.api.modules.financeiro.categoria.DTOs;

/** Endpoint próprio (não campo em {@link CategoriaResponse}) pra não fazer um COUNT por categoria em toda a listagem. */
public record ContagemMovimentacoesResponse(long total) {
}
