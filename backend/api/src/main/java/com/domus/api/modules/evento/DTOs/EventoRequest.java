package com.domus.api.modules.evento.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;

public record EventoRequest(
        @NotBlank(message = "O título é obrigatório.")
        String titulo,
        String descricao,
        @NotNull(message = "A data de início é obrigatória.")
        LocalDateTime inicioEm,
        LocalDateTime fimEm,
        String local,
        String foto,
        @Positive(message = "As vagas devem ser maiores que zero.")
        Integer vagas,
        @Positive(message = "O valor deve ser maior que zero.")
        java.math.BigDecimal preco,
        Boolean exclusivoMembros,
        Boolean exclusivoBatizados,
        Boolean requerInscricao
) {}
