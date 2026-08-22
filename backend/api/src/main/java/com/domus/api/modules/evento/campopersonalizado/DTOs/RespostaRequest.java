package com.domus.api.modules.evento.campopersonalizado.DTOs;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** {@code valor}: pra MULTIPLA_ESCOLHA, o front já manda serializado como
 *  {@code "opção 1 | opção 2"} (mesmo separador salvo em texto — ver spec). */
public record RespostaRequest(
        @NotNull(message = "Campo inválido.") UUID campoId,
        String valor
) {}
