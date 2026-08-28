package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * {@code pessoaNome} serve pra exibição nos dois casos (cadastrado ou de fora);
 * {@code nomeExterno} só vem preenchido quando é o caso "de fora" — o front usa isso pra
 * saber se deve mostrar como texto editável (de fora) ou como pessoa travada/buscável
 * (cadastrado, inclusive quando {@code pessoaId} é nulo por já ter sido excluído de vez).
 */
public record ContribuinteResponse(
        UUID pessoaId,
        String pessoaNome,
        String nomeExterno,
        @JsonSerialize(using = ToStringSerializer.class)
        BigDecimal valor
) {}
