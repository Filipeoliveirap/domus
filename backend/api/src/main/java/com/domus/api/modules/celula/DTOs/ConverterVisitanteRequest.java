package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.pessoa.Vinculo;
import jakarta.validation.constraints.NotNull;

public record ConverterVisitanteRequest(@NotNull Vinculo vinculo) {}
