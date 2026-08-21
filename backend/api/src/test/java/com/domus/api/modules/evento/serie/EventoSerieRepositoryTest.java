package com.domus.api.modules.evento.serie;

import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoSerieRepositoryTest implements PostgresTestContainerSupport {

    @Autowired EventoSerieRepository repository;
    @Autowired IgrejaRepository igrejaRepository;

    @Test
    void salvaERecuperaPorIgreja() {
        Igreja igreja = new Igreja();
        igreja.setNome("Igreja Teste Série");
        igreja.setEmailContato("serie@teste.com");
        igreja = igrejaRepository.save(igreja);

        EventoSerie serie = EventoSerie.builder()
                .igreja(igreja)
                .frequencia(FrequenciaRecorrencia.SEMANAL)
                .intervalo(1)
                .diasSemana("QUINTA")
                .build();
        UUID id = repository.save(serie).getId();

        assertThat(repository.findByIdAndIgrejaId(id, igreja.getId())).isPresent();
        assertThat(repository.findByIgrejaIdAndAtivaTrue(igreja.getId()))
                .extracting(EventoSerie::getId).contains(id);
    }
}
