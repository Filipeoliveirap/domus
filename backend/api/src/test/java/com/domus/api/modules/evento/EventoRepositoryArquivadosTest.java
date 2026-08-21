package com.domus.api.modules.evento;

import com.domus.api.modules.evento.inscricao.InscricaoEvento;
import com.domus.api.modules.evento.inscricao.InscricaoRepository;
import com.domus.api.modules.evento.inscricao.StatusInscricao;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import com.domus.api.shared.testcontainers.PostgresTestContainerSupport;

/** Arquivados + exclusão definitiva de Evento — mesma família de bugs já corrigida em Célula/Ministério. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EventoRepositoryArquivadosTest implements PostgresTestContainerSupport {

    @Autowired EventoRepository eventoRepository;
    @Autowired InscricaoRepository inscricaoRepository;
    @Autowired IgrejaRepository igrejaRepository;
    @Autowired PessoaRepository pessoaRepository;
    @Autowired EntityManager entityManager;

    Igreja igreja;

    @BeforeEach
    void setup() {
        igreja = igrejaRepository.save(Igreja.builder()
                .nome("Igreja Teste Evento " + UUID.randomUUID())
                .emailContato("evento-" + UUID.randomUUID() + "@teste.com")
                .build());
        entityManager.flush();
    }

    private Evento evento() {
        return eventoRepository.save(Evento.builder()
                .igreja(igreja).titulo("Culto " + UUID.randomUUID())
                .inicioEm(LocalDateTime.now().plusDays(1))
                .build());
    }

    private Pessoa pessoa() {
        return pessoaRepository.save(Pessoa.builder()
                .igreja(igreja).nome("Fulano " + UUID.randomUUID()).vinculo(Vinculo.MEMBRO).build());
    }

    @Test
    void hardDeleteByIdFalhaSeAindaTemInscricao_ordemImporta() {
        Evento e = evento();
        InscricaoEvento inscricao = inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(e).pessoa(pessoa()).status(StatusInscricao.CONFIRMADA).build());
        entityManager.flush();
        entityManager.clear();

        // Postgres invalida a transação após o erro — só dá pra provar que estourou.
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> {
            eventoRepository.hardDeleteById(e.getId());
            entityManager.flush();
        }).isInstanceOf(Exception.class);

        assertThat(inscricao.getId()).isNotNull();
    }

    @Test
    void apagarInscricoesDepoisHardDeleteFunciona() {
        Evento e = evento();
        inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(e).pessoa(pessoa()).status(StatusInscricao.CONFIRMADA).build());
        entityManager.flush();
        entityManager.clear();

        List<InscricaoEvento> inscricoes = inscricaoRepository.findByEventoId(e.getId());
        inscricaoRepository.deleteAll(inscricoes);
        inscricaoRepository.flush();
        eventoRepository.hardDeleteById(e.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(eventoRepository.findByIdAndIgrejaIdIncluindoArquivados(e.getId(), igreja.getId())).isEmpty();
        assertThat(inscricaoRepository.findByEventoId(e.getId())).isEmpty();
    }

    @Test
    void findArquivadosPorIgrejaTrazSoOsArquivados() {
        Evento ativo = evento();
        Evento arquivado = evento();
        eventoRepository.delete(arquivado);
        entityManager.flush();
        entityManager.clear();

        List<Evento> arquivados = eventoRepository.findArquivadosPorIgreja(igreja.getId());

        assertThat(arquivados).extracting(Evento::getId).containsExactly(arquivado.getId());
        assertThat(arquivados).extracting(Evento::getId).doesNotContain(ativo.getId());
    }

    @Test
    void restaurarPorIdSoRestauraDaIgrejaCerta() {
        Evento e = evento();
        UUID id = e.getId();
        eventoRepository.delete(e);
        entityManager.flush();
        entityManager.clear();

        int linhasOutraIgreja = eventoRepository.restaurarPorId(id, UUID.randomUUID());
        assertThat(linhasOutraIgreja).isZero();

        int linhas = eventoRepository.restaurarPorId(id, igreja.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(linhas).isEqualTo(1);
        assertThat(eventoRepository.findByIdAndIgrejaId(id, igreja.getId())).isPresent();
    }

    @Test
    void countByEventoIdContaInscricaoDeEventoArquivado() {
        Evento e = evento();
        inscricaoRepository.save(InscricaoEvento.builder()
                .igreja(igreja).evento(e).pessoa(pessoa()).status(StatusInscricao.CONFIRMADA).build());
        entityManager.flush();
        entityManager.clear();

        eventoRepository.delete(e);
        entityManager.flush();
        entityManager.clear();

        assertThat(inscricaoRepository.countByEventoId(e.getId())).isEqualTo(1);
    }
}
