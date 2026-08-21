package com.domus.api.modules.igreja.exclusao.DTO;

import org.junit.jupiter.api.Test;

import static com.domus.api.shared.util.ValidacaoTestSupport.VALIDATOR;
import static org.assertj.core.api.Assertions.assertThat;

class AgendarExclusaoRequestTest {

    @Test
    void camposValidos_naoGeraViolacao() {
        var request = new AgendarExclusaoRequest("Igreja Teste", "senha123", null);
        assertThat(VALIDATOR.validate(request)).isEmpty();
    }

    @Test
    void senhaAcimaDe255_recusaComViolacao() {
        var request = new AgendarExclusaoRequest("Igreja Teste", "a".repeat(256), null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void googleIdTokenAcimaDe4096_recusaComViolacao() {
        var request = new AgendarExclusaoRequest("Igreja Teste", null, "a".repeat(4097));
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }

    @Test
    void nomeConfirmacaoVazio_recusaComViolacao() {
        var request = new AgendarExclusaoRequest("", "senha123", null);
        assertThat(VALIDATOR.validate(request)).isNotEmpty();
    }
}
