package com.domus.api.modules.celula.DTOs;

import com.domus.api.modules.celula.DiaSemana;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * horario era String livre sem @Pattern — o service faz LocalTime.parse(data.horario()),
 * que estourava DateTimeParseException não tratada (500) pra qualquer string malformada.
 */
class CelulaRequestTest {

    @Test
    void horarioValido_naoGeraViolacao() {
        var request = new CelulaRequest("Célula Teste", DiaSemana.QUARTA, "19:30", null);
        Set<?> violacoes = VALIDATOR.validate(request);
        assertThat(violacoes).isEmpty();
    }

    @Test
    void horarioNuloOuVazio_naoGeraViolacao() {
        assertThat(VALIDATOR.validate(new CelulaRequest("Célula", null, null, null))).isEmpty();
        assertThat(VALIDATOR.validate(new CelulaRequest("Célula", null, "", null))).isEmpty();
    }

    @Test
    void horarioMalformado_recusaComViolacao() {
        var request = new CelulaRequest("Célula Teste", DiaSemana.QUARTA, "25:99", null);
        Set<?> violacoes = VALIDATOR.validate(request);
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void horarioTextoLivre_recusaComViolacao() {
        var request = new CelulaRequest("Célula Teste", DiaSemana.QUARTA, "às sete da noite", null);
        Set<?> violacoes = VALIDATOR.validate(request);
        assertThat(violacoes).isNotEmpty();
    }

    @Test
    void nomeVazio_recusaComViolacao() {
        var request = new CelulaRequest("", DiaSemana.QUARTA, "19:30", UUID.randomUUID());
        Set<?> violacoes = VALIDATOR.validate(request);
        assertThat(violacoes).isNotEmpty();
    }
}
