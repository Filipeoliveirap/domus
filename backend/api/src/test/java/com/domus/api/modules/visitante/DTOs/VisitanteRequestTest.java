package com.domus.api.modules.visitante.DTOs;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

class VisitanteRequestTest {

    private VisitanteRequest base(String telefone, Integer quantidadeFilhos, String observacoes) {
        return new VisitanteRequest("Fulano", telefone, null, null, null, null, null, quantidadeFilhos, observacoes);
    }

    @Test
    void camposValidos_naoGeraViolacao() {
        assertThat(VALIDATOR.validate(base("11987654321", 2, "obs curta"))).isEmpty();
    }

    @Test
    void telefoneComLetras_recusaComViolacao() {
        assertThat(VALIDATOR.validate(base("não tenho telefone", null, null))).isNotEmpty();
    }

    @Test
    void telefoneNulo_naoGeraViolacao() {
        assertThat(VALIDATOR.validate(base(null, null, null))).isEmpty();
    }

    @Test
    void quantidadeFilhosNegativa_recusaComViolacao() {
        assertThat(VALIDATOR.validate(base(null, -1, null))).isNotEmpty();
    }

    @Test
    void quantidadeFilhosImplausivel_recusaComViolacao() {
        assertThat(VALIDATOR.validate(base(null, 31, null))).isNotEmpty();
    }

    @Test
    void observacoesAcimaDe5000_recusaComViolacao() {
        assertThat(VALIDATOR.validate(base(null, null, "a".repeat(5001)))).isNotEmpty();
    }
}
