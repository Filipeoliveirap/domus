package com.domus.api.modules.evento;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EventoDefaultsTest {

    @Test
    void controlaPresenca_defaultFalse_quandoNaoInformado() {
        Evento evento = Evento.builder()
                .titulo("Culto")
                .inicioEm(java.time.LocalDateTime.now())
                .build();

        assertThat(evento.isControlaPresenca()).isFalse();
    }
}
