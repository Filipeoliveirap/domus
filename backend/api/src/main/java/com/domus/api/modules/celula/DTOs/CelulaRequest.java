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
        /** String vazia/null = sem horário definido; HH:mm ou HH:mm:ss (o front sempre manda com
         *  segundos), parseado como LocalTime no service — os dois formatos batem com LocalTime.parse. */
        @Pattern(regexp = "^$|^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$", message = "Horário inválido, use o formato HH:mm.")
        String horario,
        UUID fotoId
) {}
