package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.DiaSemana;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalTime;
import java.util.UUID;

public record CelulaRequest(
        @NotBlank @Size(max = 150) String nome,
        DiaSemana diaSemana,
        /** String vazia/null = sem horário definido; formato HH:mm, parseado como LocalTime no service. */
        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d$", message = "Horário inválido, use o formato HH:mm.")
        String horario,
        UUID fotoId
) {}
