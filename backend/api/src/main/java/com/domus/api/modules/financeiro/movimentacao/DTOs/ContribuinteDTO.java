package com.domus.api.modules.financeiro.movimentacao.DTOs;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Exatamente um entre {@code pessoaId} (cadastro na igreja) e {@code nomeExterno} (pessoa de
 * fora, sem cadastro — ex.: doação de visitante avulso) — validado em
 * {@code MovimentacaoFinanceiraService.validarContribuintes}, não aqui, porque a regra
 * envolve dois campos e bean validation por campo isolado não expressa isso bem.
 */
public record ContribuinteDTO(
        UUID pessoaId,

        @Size(max = 255, message = "O nome deve ter no máximo 255 caracteres")
        String nomeExterno,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "Valor inválido")
        BigDecimal valor
) {}
