package com.domus.api.modules.evento.serie.DTOs;

import com.domus.api.modules.evento.serie.FrequenciaRecorrencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

class RecorrenciaRequestTest {

    @Test
    void dataFimENumeroOcorrenciasJuntosRecusaComViolacao() {
        var request = new RecorrenciaRequest(
                FrequenciaRecorrencia.DIARIA, 1, Set.of(), null,
                LocalDate.now().plusDays(10), 5);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void somenteDataFimNaoGeraViolacao() {
        var request = new RecorrenciaRequest(
                FrequenciaRecorrencia.DIARIA, 1, Set.of(), null,
                LocalDate.now().plusDays(10), null);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void semFimNemContagemNaoGeraViolacao() {
        var request = new RecorrenciaRequest(
                FrequenciaRecorrencia.SEMANAL, 1, Set.of(com.domus.api.modules.celula.DiaSemana.QUINTA),
                null, null, null);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }
}
