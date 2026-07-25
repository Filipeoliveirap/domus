package com.domus.api.modules.visitante.DTOs;

import jakarta.validation.constraints.NotNull;

public record ToggleRequest(@NotNull boolean valor) {}
