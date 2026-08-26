package com.domus.api.modules.pagamento.cobranca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CobrancaEventoTest {

    @Test
    void aceitaPessoaIdEAcompanhanteIdOsDoisNulosParaConvidadoSemCadastro() {
        assertThatCode(() -> new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, null,
                BigDecimal.TEN, Instant.now().plusSeconds(600), null, null))
            .doesNotThrowAnyException();
    }

    @Test
    void recusaPessoaIdEAcompanhanteIdOsDoisPreenchidos() {
        assertThatThrownBy(() -> new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
