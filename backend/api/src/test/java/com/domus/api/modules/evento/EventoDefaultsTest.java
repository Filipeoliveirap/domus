package com.domus.api.modules.evento;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Flush + clear do cache de 1º nível e relê do banco — só assim um default de banco desconectado do Java seria pego. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoDefaultsTest implements PostgresTestContainerSupport {

    @Autowired
    IgrejaRepository igrejaRepository;

    @Autowired
    EventoRepository eventoRepository;

    @Autowired
    PessoaRepository pessoaRepository;

    @Autowired
    InscricaoRepository inscricaoRepository;

    @Autowired
    EntityManager entityManager;

    @Test
    void controlaPresenca_defaultFalse_quandoNaoInformado_persistidoNoBanco() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Defaults " + UUID.randomUUID())
                .emailContato("defaults-" + UUID.randomUUID() + "@teste.com")
                .build());

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja)
                .titulo("Culto")
                .inicioEm(LocalDateTime.now())
                .build());

        entityManager.flush();
        entityManager.clear();

        Evento recarregado = eventoRepository.findById(evento.getId()).orElseThrow();
        assertThat(recarregado.isControlaPresenca()).isFalse();
    }

    @Test
    void compareceu_defaultFalse_quandoNaoInformado_persistidoNoBanco() {
        Igreja igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Defaults Presenca " + UUID.randomUUID())
                .emailContato("defaults-presenca-" + UUID.randomUUID() + "@teste.com")
                .build());

        Evento evento = eventoRepository.save(Evento.builder()
                .igreja(igreja)
                .titulo("Retiro")
                .inicioEm(LocalDateTime.now())
                .build());

        Pessoa pessoa = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja)
                .nome("Fulano de Tal")
                .email("fulano-" + UUID.randomUUID() + "@teste.com")
                .vinculo(Vinculo.MEMBRO)
                .build());

        InscricaoEvento inscricao = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja)
                .evento(evento)
                .pessoa(pessoa)
                .build());

        InscricaoEvento convidado = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja)
                .evento(evento)
                .nomeConvidado("Convidado de Fulano")
                .convidadoPor(pessoa)
                .build());

        entityManager.flush();
        entityManager.clear();

        InscricaoEvento inscricaoRecarregada = inscricaoRepository.findById(inscricao.getId()).orElseThrow();
        assertThat(inscricaoRecarregada.isCompareceu()).isFalse();

        InscricaoEvento convidadoRecarregado = inscricaoRepository.findById(convidado.getId()).orElseThrow();
        assertThat(convidadoRecarregado.isCompareceu()).isFalse();
    }
}
