package com.domus.api.modules.financeiro.movimentacao.DTOs;

import com.domus.api.modules.financeiro.movimentacao.TipoMovimentacao;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MovimentacaoRequestDTO(
        @NotNull(message = "O tipo é obrigatório")
        TipoMovimentacao tipo,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 13, fraction = 2, message = "Valor inválido")
        BigDecimal valor,

        @NotNull(message = "A categoria é obrigatória")
        UUID categoriaId,

        @NotNull(message = "A data é obrigatória")
        LocalDate dataMovimentacao,

        @NotNull(message = "A lista de contribuintes é obrigatória (pode ser vazia)")
        List<ContribuinteDTO> contribuintes,

        @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres")
        String descricao
) {}