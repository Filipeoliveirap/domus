package com.domus.api.modules.contapagar.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.domus.api.modules.contapagar.RecorrenciaFrequencia;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;

/** Configuração de recorrência de uma conta a pagar. */
public record RecorrenciaDTO(
    @JsonAlias({"tipo"})
    RecorrenciaFrequencia frequencia,

    LocalDate ate,

    @JsonAlias({"totalParcelas", "total_parcelas"})
    @Min(1) @Max(99) Integer vezes,

    @JsonAlias({"dia_ancora"})
    @Min(1) @Max(28) Integer diaAncora
) {}
