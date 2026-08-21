package com.domus.api.modules.evento.campopersonalizado;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CampoPersonalizadoEventoRepositoryTest implements PostgresTestContainerSupport {

    @Autowired CampoPersonalizadoEventoRepository repository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EventoRepository eventoRepository;

    @Test
    void salvaERecuperaOrdenadoPorOrdem() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste Campo Personalizado");
        igreja.setEmailContato("campo@teste.com");
        igreja = igrejaRepository.save(igreja);

        Evento evento = Evento.builder()
                .igreja(igreja).titulo("Retiro de Jovens")
                .inicioEm(LocalDateTime.now().plusDays(10))
                .build();
        evento = eventoRepository.save(evento);

        CampoPersonalizadoEvento segundo = CampoPersonalizadoEvento.builder()
                .igreja(igreja).evento(evento).label("Tamanho da camiseta")
                .tipo(TipoCampoPersonalizado.OPCAO_UNICA).ordem(1).build();
        segundo.setOpcoesComoLista(List.of("P", "M", "G"));
        repository.save(segundo);

        CampoPersonalizadoEvento primeiro = CampoPersonalizadoEvento.builder()
                .igreja(igreja).evento(evento).label("Restrição alimentar")
                .tipo(TipoCampoPersonalizado.TEXTO_CURTO).ordem(0).build();
        repository.save(primeiro);

        List<CampoPersonalizadoEvento> campos =
                repository.findByEventoIdAndIgrejaIdOrderByOrdemAsc(evento.getId(), igreja.getId());

        assertThat(campos).extracting(CampoPersonalizadoEvento::getLabel)
                .containsExactly("Restrição alimentar", "Tamanho da camiseta");
        assertThat(campos.get(1).getOpcoesComoLista()).containsExactly("P", "M", "G");
    }
}
