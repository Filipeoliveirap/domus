package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** {@code id} nulo = campo novo; preenchido = atualiza o existente. */
public record CampoPersonalizadoRequest(
        UUID id,
        @NotBlank(message = "O rótulo é obrigatório.")
        @Size(max = 120, message = "Máximo 120 caracteres.")
        String label,
        @Size(max = 160, message = "Máximo 160 caracteres.")
        String placeholder,
        @NotNull(message = "Escolha o tipo do campo.")
        TipoCampoPersonalizado tipo,
        List<String> opcoes,
        boolean obrigatorio,
        boolean visivelAoPublico,
        int ordem
) {
    @AssertTrue(message = "Informe pelo menos uma opção.")
    public boolean isOpcoesValidas() {
        boolean precisaDeOpcoes = tipo == TipoCampoPersonalizado.OPCAO_UNICA
                || tipo == TipoCampoPersonalizado.MULTIPLA_ESCOLHA;
        return !precisaDeOpcoes || (opcoes != null && !opcoes.isEmpty());
    }
}
