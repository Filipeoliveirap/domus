package com.domus.api.modules.evento.campopersonalizado.DTOs;

import com.domus.api.modules.evento.campopersonalizado.TipoCampoPersonalizado;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CampoPersonalizadoRequestTest {

    private final Validator validator;

    CampoPersonalizadoRequestTest() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void opcaoUnicaSemOpcoesEInvalida() {
        var request = new CampoPersonalizadoRequest(
                null, "Tamanho da camiseta", null, TipoCampoPersonalizado.OPCAO_UNICA,
                List.of(), false, true, 0);

        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void textoCurtoSemOpcoesEValido() {
        var request = new CampoPersonalizadoRequest(
                UUID.randomUUID(), "Restrição alimentar", "Ex.: sem lactose",
                TipoCampoPersonalizado.TEXTO_CURTO, null, true, true, 1);

        assertThat(validator.validate(request)).isEmpty();
    }
}
