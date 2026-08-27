package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RespostaCampoPersonalizadoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired RespostaCampoPersonalizadoRepository respostaRepository;
    @Autowired CampoPersonalizadoEventoRepository campoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired IgrejaRepository igrejaRepository;

    @Test
    void encontraRespostaPorCampoEInscricao() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste Resposta");
        igreja.setEmailContato("resposta@teste.com");
        igreja = igrejaRepository.save(igreja);

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Retiro").inicioEm(LocalDateTime.now().plusDays(5)).build());

        CampoPersonalizadoEvento campo = campoRepository.save(CampoPersonalizadoEvento.builder()
                .igreja(igreja).evento(evento).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).build());

        InscricaoEvento inscricao = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).status(StatusInscricao.CONFIRMADA).build());

        respostaRepository.save(RespostaCampoPersonalizado.builder()
                .campo(campo).inscricao(inscricao).valor("Sem lactose").build());

        Optional<RespostaCampoPersonalizado> encontrada = respostaRepository
                .findByCampoIdAndInscricaoId(campo.getId(), inscricao.getId());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().getValor()).isEqualTo("Sem lactose");
    }
}
