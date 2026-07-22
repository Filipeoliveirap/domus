package com.domus.api.modules.evento.local.DTOs;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LocalEventoRequest(
        @NotBlank(message = "O nome do local é obrigatório.")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.")
        String nome,
        @Positive(message = "A capacidade deve ser maior que zero.")
        Integer capacidade,
        String cepLogradouroNumero,
        String complementoBairroCidadeUf
) {}
