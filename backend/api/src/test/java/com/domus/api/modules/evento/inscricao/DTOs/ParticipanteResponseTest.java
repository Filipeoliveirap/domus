package com.domus.api.modules.evento.inscricao.DTOs;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.visitante.Visitante;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ParticipanteResponseTest {

    @Test
    void mostraNomeConvidadoEConvidadoPorQuandoSemCadastro() {
        Igreja igreja = new Igreja();
        igreja.setId(UUID.randomUUID());
        InscricaoEvento i = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja)
                .evento(Evento.builder().id(UUID.randomUUID()).igreja(igreja).titulo("Culto").build())
                .status(StatusInscricao.CONFIRMADA)
                .nomeConvidado("Pedro de Fora")
                .build();
        Pessoa convidante = Pessoa.builder().id(UUID.randomUUID()).nome("Lucas").build();

        ParticipanteResponse resp = ParticipanteResponse.from(i, null, convidante);

        assertThat(resp.nome()).isEqualTo("Pedro de Fora");
        assertThat(resp.convidadoPorNome()).isEqualTo("Lucas");
        assertThat(resp.visitanteId()).isNull();
    }

    @Test
    void mostraVisitanteIdQuandoConvidadoVeioDeVisitanteCadastrado() {
        Igreja igreja = new Igreja();
        igreja.setId(UUID.randomUUID());
        UUID visitanteId = UUID.randomUUID();
        Visitante visitante = Visitante.builder().id(visitanteId).igreja(igreja).nome("Pedro").build();
        InscricaoEvento i = InscricaoEvento.builder()
                .id(UUID.randomUUID()).igreja(igreja)
                .evento(Evento.builder().id(UUID.randomUUID()).igreja(igreja).titulo("Culto").build())
                .status(StatusInscricao.CONFIRMADA)
                .nomeConvidado("Pedro de Fora")
                .visitante(visitante)
                .build();

        ParticipanteResponse resp = ParticipanteResponse.from(i, null, null);

        assertThat(resp.visitanteId()).isEqualTo(visitanteId);
    }
}
