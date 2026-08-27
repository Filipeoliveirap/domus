package com.domus.api.modules.pagamento.cobranca;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class CobrancaEventoTest {

    @Test
    void aceitaPessoaIdNuloParaConvidadoSemCadastro() {
        // Convidado sem cadastro (V30): pessoaId nulo, resolvido só por inscricaoId
        // (ver CobrancaController) — cada "acompanhante" virou sua própria InscricaoEvento,
        // então CobrancaEvento não tem mais um segundo campo de pagador pra conflitar.
        assertThatCode(() -> new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null,
                BigDecimal.TEN, Instant.now().plusSeconds(600), null, null))
            .doesNotThrowAnyException();
    }

    @Test
    void aceitaPessoaIdPreenchidaParaPagadorComCadastro() {
        var cobranca = new CobrancaEvento(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.TEN, Instant.now().plusSeconds(600), UUID.randomUUID(), null);

        assertThat(cobranca.getPessoaId()).isNotNull();
    }
}
