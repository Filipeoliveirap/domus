package com.domus.api.modules.evento.serie.DTOs;

import com.domus.api.modules.celula.DiaSemana;
import com.domus.api.modules.evento.serie.FrequenciaRecorrencia;
import com.domus.api.modules.evento.serie.TipoRecorrenciaMensal;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.util.Set;

public record RecorrenciaRequest(
        @NotNull(message = "A frequência é obrigatória.")
        FrequenciaRecorrencia frequencia,
        @Positive(message = "O intervalo deve ser maior que zero.")
        Integer intervalo,
        /** Só usado quando {@code frequencia == SEMANAL}. */
        Set<DiaSemana> diasSemana,
        /** Só usado quando {@code frequencia == MENSAL}. */
        TipoRecorrenciaMensal tipoRecorrenciaMensal,
        LocalDate dataFim,
        @Positive(message = "O número de ocorrências deve ser maior que zero.")
        Integer numeroOcorrencias
) {
    @AssertTrue(message = "Escolha uma data de fim OU um número de ocorrências, não os dois.")
    public boolean isFimValido() {
        return dataFim == null || numeroOcorrencias == null;
    }
}
