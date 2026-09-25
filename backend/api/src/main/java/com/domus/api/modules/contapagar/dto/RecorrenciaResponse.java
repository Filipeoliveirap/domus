package com.domus.api.modules.contapagar.dto;

import com.domus.api.modules.contapagar.RecorrenciaFrequencia;

import java.time.LocalDate;

/** Dados de recorrência em uma resposta. */
public record RecorrenciaResponse(
    String frequencia,
    LocalDate ate,
    Integer vezes,
    Integer diaAncora,
    Integer ocorrenciasRestantes,
    Boolean ehGeradora
) {}
