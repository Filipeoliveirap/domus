package com.domus.api.modules.evento.inscricao;

import com.domus.api.modules.evento.Evento;
import com.domus.api.modules.evento.EventoRepository;
import com.domus.api.modules.igreja.Igreja;
import com.domus.api.modules.igreja.IgrejaRepository;
import com.domus.api.modules.pessoa.Pessoa;
import com.domus.api.modules.pessoa.PessoaRepository;
import com.domus.api.modules.pessoa.Vinculo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/**
 * Regressão: pessoa excluída definitivamente continua contando no total de inscritos
 * (`contarPessoasConfirmadas`) — mas as três consultas que listam quem está inscrito faziam
 * INNER JOIN com pessoa e sumiam com a linha, deixando o total (2) maior que a lista (1).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InscricaoRepositoryPessoaRemovidaTest implements PostgresTestContainerSupport {

    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired EventoRepository eventoRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;
    Evento evento;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Inscrito Removido " + UUID.randomUUID())
                .emailContato("insc-" + UUID.randomUUID() + "@teste.com")
                .build());
        evento = eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Evento " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(1)).build());
    }

    @Test
    void listagens_continuamMostrandoInscricaoDePessoaRemovida() {
        Pessoa presente = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Presente " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        Pessoa removida = pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Removida " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
        inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(presente).status(StatusInscricao.CONFIRMADA).build());
        inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(evento).pessoa(removida).status(StatusInscricao.CONFIRMADA).build());
        entityManager.flush();

        // Simula a exclusão definitiva: desvincula sem apagar a linha.
        inscricaoRepository.desvincularPessoa(removida.getId());
        entityManager.flush();
        entityManager.clear();

        long total = inscricaoRepository.contarPessoasConfirmadas(evento.getId());
        assertThat(total).isEqualTo(2);

        assertThat(inscricaoRepository.listarPorEvento(evento.getId())).hasSize(2);

        var pagina = inscricaoRepository.listarIdsPaginadoPorEvento(evento.getId(), null, PageRequest.of(0, 20));
        assertThat(pagina.getTotalElements()).isEqualTo(2);

        var comDetalhes = inscricaoRepository.listarComDetalhesPorIds(pagina.getContent());
        assertThat(comDetalhes).hasSize(2);
        assertThat(comDetalhes).anyMatch(i -> i.getPessoa() == null);
        assertThat(comDetalhes).anyMatch(i -> i.getPessoa() != null && i.getPessoa().getId().equals(presente.getId()));
    }
}
