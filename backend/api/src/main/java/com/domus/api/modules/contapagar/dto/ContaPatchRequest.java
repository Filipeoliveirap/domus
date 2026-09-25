package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.contapagar.RecorrenciaFrequencia;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Request para atualização parcial de uma conta a pagar (campos em Optional para PATCH).
 *  Campos não fornecidos (null) são ignorados na edição. */
public record ContaPatchRequest(
    UUID categoriaId,
    @Valid BeneficiarioDTO beneficiario,
    String cnpjBeneficiario,
    String descricao,
    BigDecimal valor,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate vencimento,
    @JsonFormat(pattern = "yyyy-MM-dd") LocalDate competencia,
    String linhaDigitavel,
    String documentoNumero,
    String observacoes,
    UUID anexoId,
    RecorrenciaDTO recorrencia
) {}
