package com.domus.api.modules.evento.convite.DTOs;

import com.domus.api.modules.evento.campopersonalizado.DTOs.RespostaRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EntrarConviteRequest(
        @NotBlank(message = "O nome é obrigatório.")
        @Size(max = 255, message = "Máximo 255 caracteres.")
        String nome,
        @Size(max = 20, message = "Máximo 20 caracteres.")
        String telefone,
        @Valid
        List<RespostaRequest> respostas
) {}
