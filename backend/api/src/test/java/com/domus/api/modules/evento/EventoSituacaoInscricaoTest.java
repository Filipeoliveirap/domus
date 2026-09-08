package com.domus.api.modules.evento;

import com.domus.api.modules.igreja.Igreja;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EventoSituacaoInscricaoTest {

    private Evento evento(LocalDateTime inicioEm, LocalDateTime inscricoesAte) {
        return Evento.builder()
                .igreja(new Igreja())
                .titulo("Retiro")
                .inicioEm(inicioEm)
                .inscricoesAte(inscricoesAte)
                .requerInscricao(true)
                .build();
    }

    @Test
    void aberta_quando_nao_ha_prazo_e_evento_no_futuro() {
        var e = evento(LocalDateTime.now().plusDays(10), null);
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ABERTA);
    }

    @Test
    void aberta_quando_prazo_ainda_nao_venceu() {
        var e = evento(LocalDateTime.now().plusDays(10), LocalDateTime.now().plusDays(3));
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ABERTA);
    }

    @Test
    void encerrada_por_prazo_quando_prazo_venceu_e_evento_nao_comecou() {
        var e = evento(LocalDateTime.now().plusDays(10), LocalDateTime.now().minusHours(1));
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_PRAZO);
    }

    @Test
    void encerrada_por_inicio_tem_precedencia_sobre_prazo() {
        // evento já começou (inicioEm no passado) E prazo também no passado
        var e = evento(LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(3));
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_INICIO);
    }

    @Test
    void encerrada_por_inicio_quando_evento_comecou_e_nao_ha_prazo() {
        var e = evento(LocalDateTime.now().minusHours(2), null);
        assertThat(e.getSituacaoInscricao()).isEqualTo(SituacaoInscricao.ENCERRADA_POR_INICIO);
    }
}
