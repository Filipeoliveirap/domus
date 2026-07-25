package com.domus.api.modules.usuario.DTO;

import jakarta.validation.constraints.NotBlank;

public record CapacidadeRequest(@NotBlank String capacidade) {}
