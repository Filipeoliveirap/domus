package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.PapelCelula;
import jakarta.validation.constraints.NotNull;

public record AtualizarPapelCelulaRequest(@NotNull(message = "O papel é obrigatório.") PapelCelula papel) {}
