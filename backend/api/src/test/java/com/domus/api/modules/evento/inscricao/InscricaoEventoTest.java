package com.domus.api.modules.evento.inscricao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InscricaoEventoTest {

    @Test
    void estaAguardandoPagamentoEhVerdadeiroSoNesseStatus() {
        InscricaoEvento aguardando = InscricaoEvento.builder()
                .status(StatusInscricao.AGUARDANDO_PAGAMENTO).build();
        InscricaoEvento confirmada = InscricaoEvento.builder()
                .status(StatusInscricao.CONFIRMADA).build();
        InscricaoEvento cancelada = InscricaoEvento.builder()
                .status(StatusInscricao.CANCELADA).build();

        assertThat(aguardando.estaAguardandoPagamento()).isTrue();
        assertThat(confirmada.estaAguardandoPagamento()).isFalse();
        assertThat(cancelada.estaAguardandoPagamento()).isFalse();
    }
}
