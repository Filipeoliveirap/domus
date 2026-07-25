package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.DiaSemana;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;

public record CelulaRequest(
        @NotBlank @Size(max = 150) String nome,
        DiaSemana diaSemana,
        String horario
) {}
