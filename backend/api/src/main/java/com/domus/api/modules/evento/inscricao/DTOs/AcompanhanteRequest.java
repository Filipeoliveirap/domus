package com.domus.api.modules.evento.inscricao.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcompanhanteRequest(
        @NotBlank(message = "O nome do convidado é obrigatório.")
        @Size(max = 255)
        String nome,
        @Size(max = 20)
        String telefone
) {}
